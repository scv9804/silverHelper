// 작업별 소요 시간, 토큰, 한도 사용률을 docs/metrics/ledger.md에 기록한다.
//
//   node scripts/track.mjs start <작업ID> <에이전트> <claude|codex> ["작업 설명"]
//   node scripts/track.mjs end   <작업ID> [PR 링크나 비고]
//   node scripts/track.mjs status
//   node scripts/track.mjs report --from <ISO> --to <ISO> [--session <id>] [--cwd <path>] [--model claude|codex]
//
// 토큰은 start를 실행한 폴더(worktree)에서 돌아간 세션만 집계한다.
// 한도 %는 계정 전체 기준이라 동시에 돌아간 다른 에이전트 사용량도 섞인다.
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import {
  claudeLimits,
  claudeUsage,
  codexLimits,
  codexUsage,
  fmtDuration,
  fmtInt,
  fmtKst,
  metricsDir,
  repoRoot,
} from './lib.mjs'

const [cmd, ...args] = process.argv.slice(2)
const activeFile = (id) => path.join(metricsDir(), 'active', `${id}.json`)

const limitsOf = (model) => (model === 'codex' ? codexLimits() : claudeLimits())
const usageOf = (model, q) => (model === 'codex' ? codexUsage(q) : claudeUsage(q))
const pct = (v) => (v === undefined || v === null ? '?' : `${Math.round(v)}`)
const pctRange = (a, b, key) => `${pct(a?.[key])}→${pct(b?.[key])}%`

function ensureLedger() {
  const file = path.join(repoRoot(), 'docs', 'metrics', 'ledger.md')
  if (!fs.existsSync(file)) throw new Error(`ledger 없음: ${file}`)
  return file
}

function opt(name) {
  const i = args.indexOf(`--${name}`)
  return i >= 0 ? args[i + 1] : undefined
}

switch (cmd) {
  case 'start': {
    const [id, agent, model, ...desc] = args
    if (!id || !agent || !['claude', 'codex'].includes(model)) {
      console.error('사용법: start <작업ID> <에이전트> <claude|codex> ["설명"]')
      process.exit(1)
    }
    if (fs.existsSync(activeFile(id))) {
      console.error(`이미 진행 중인 작업: ${id}`)
      process.exit(1)
    }
    const rec = {
      id,
      agent,
      model,
      desc: desc.join(' '),
      // backend 같은 하위 폴더에서 실행해도 worktree 전체를 집계 범위로 잡는다
      cwd: execSync('git rev-parse --show-toplevel').toString().trim(),
      start: Date.now(),
      limitsBefore: limitsOf(model),
    }
    fs.writeFileSync(activeFile(id), JSON.stringify(rec, null, 2))
    console.log(`▶ ${id} 시작 (${agent}/${model}) ${fmtKst(rec.start)} KST`)
    break
  }

  case 'end': {
    const [id, ...noteParts] = args
    if (!id || !fs.existsSync(activeFile(id))) {
      console.error(`진행 중인 작업이 아님: ${id}`)
      process.exit(1)
    }
    const rec = JSON.parse(fs.readFileSync(activeFile(id), 'utf8'))
    const end = Date.now()
    const u = usageOf(rec.model, { from: rec.start, to: end, cwd: rec.cwd })
    const after = limitsOf(rec.model)
    const total = u.input + u.output + u.cacheRead + u.cacheWrite
    const note = [rec.desc, noteParts.join(' ')].filter(Boolean).join(' / ')
    const row =
      `| ${id} | ${rec.agent} (${rec.model}) | ${fmtKst(rec.start)} | ${fmtKst(end)} | ${fmtDuration(end - rec.start)} ` +
      `| ${fmtInt(u.input)} | ${fmtInt(u.output)} | ${fmtInt(u.cacheRead)} | ${fmtInt(u.cacheWrite)} | ${fmtInt(total)} ` +
      `| ${pctRange(rec.limitsBefore, after, 'fiveHour')} | ${pctRange(rec.limitsBefore, after, 'weekly')} | ${note} |\n`
    fs.appendFileSync(ensureLedger(), row)
    fs.unlinkSync(activeFile(id))
    console.log(`■ ${id} 종료: ${fmtDuration(end - rec.start)}, 총 ${fmtInt(total)} 토큰 (응답 ${u.messages}회)`)
    console.log(row.trim())
    break
  }

  case 'status': {
    const dir = path.join(metricsDir(), 'active')
    const files = fs.readdirSync(dir).filter((f) => f.endsWith('.json'))
    if (!files.length) console.log('진행 중인 작업 없음')
    for (const f of files) {
      const r = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'))
      console.log(`${r.id}  ${r.agent}/${r.model}  ${fmtDuration(Date.now() - r.start)} 경과  ${r.cwd}`)
    }
    console.log('Claude 한도:', JSON.stringify(claudeLimits()))
    console.log('Codex 한도:', JSON.stringify(codexLimits()))
    break
  }

  case 'report': {
    const from = Date.parse(opt('from'))
    const to = opt('to') ? Date.parse(opt('to')) : Date.now()
    const model = opt('model') ?? 'claude'
    const u = usageOf(model, { from, to, cwd: opt('cwd'), sessionId: opt('session') })
    console.log(JSON.stringify({ ...u, total: u.input + u.output + u.cacheRead + u.cacheWrite, duration: fmtDuration(to - from) }, null, 2))
    break
  }

  default:
    console.log('명령: start | end | status | report')
}
