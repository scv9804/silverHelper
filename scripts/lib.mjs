// 사용량 기록 스크립트 공용 함수
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

// worktree에서 실행해도 항상 메인 저장소 루트의 .metrics를 쓰도록 git common dir 기준으로 찾는다
export function repoRoot(cwd = process.cwd()) {
  const common = execSync('git rev-parse --path-format=absolute --git-common-dir', { cwd })
    .toString()
    .trim()
  return path.dirname(common)
}

export function metricsDir(cwd) {
  const dir = path.join(repoRoot(cwd), '.metrics')
  fs.mkdirSync(path.join(dir, 'active'), { recursive: true })
  return dir
}

export const normPath = (p) => path.resolve(p).replace(/[\\/]+$/, '').toLowerCase()

function* walk(dir, filter) {
  if (!fs.existsSync(dir)) return
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) yield* walk(p, filter)
    else if (filter(p)) yield p
  }
}

function* jsonLines(file) {
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    if (!line) continue
    try {
      yield JSON.parse(line)
    } catch {
      // 기록 중인 마지막 줄은 깨져 있을 수 있음
    }
  }
}

const inWindow = (ts, from, to) => {
  const t = Date.parse(ts)
  return t >= from && t <= to
}

// Claude Code: ~/.claude/projects/**/*.jsonl 의 assistant 메시지 usage 합산
export function claudeUsage({ from, to, cwd, sessionId }) {
  const root = path.join(os.homedir(), '.claude', 'projects')
  const seen = new Set()
  const sum = { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, messages: 0 }
  const since = from - 60_000
  for (const file of walk(root, (p) => p.endsWith('.jsonl'))) {
    if (fs.statSync(file).mtimeMs < since) continue
    for (const d of jsonLines(file)) {
      const u = d?.message?.usage
      if (d.type !== 'assistant' || !u || !d.timestamp) continue
      if (!inWindow(d.timestamp, from, to)) continue
      if (sessionId && d.sessionId !== sessionId) continue
      if (cwd && (!d.cwd || normPath(d.cwd) !== normPath(cwd))) continue
      const key = `${d.message.id}:${d.requestId}`
      if (seen.has(key)) continue
      seen.add(key)
      sum.input += u.input_tokens ?? 0
      sum.output += u.output_tokens ?? 0
      sum.cacheRead += u.cache_read_input_tokens ?? 0
      sum.cacheWrite += u.cache_creation_input_tokens ?? 0
      sum.messages++
    }
  }
  return sum
}

// Codex: ~/.codex/sessions/**/rollout-*.jsonl 의 token_count 이벤트(last_token_usage) 합산
export function codexUsage({ from, to, cwd }) {
  const root = path.join(os.homedir(), '.codex', 'sessions')
  const sum = { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, messages: 0 }
  let limits = null
  const since = from - 60_000
  for (const file of walk(root, (p) => p.endsWith('.jsonl'))) {
    if (fs.statSync(file).mtimeMs < since) continue
    let sessionCwd = null
    for (const d of jsonLines(file)) {
      if (d.type === 'session_meta') sessionCwd = d.payload?.cwd ?? null
      if (d.type !== 'event_msg' || d.payload?.type !== 'token_count') continue
      if (!inWindow(d.timestamp, from, to)) continue
      if (cwd && (!sessionCwd || normPath(sessionCwd) !== normPath(cwd))) continue
      const u = d.payload.info?.last_token_usage
      if (u) {
        const cached = u.cached_input_tokens ?? 0
        sum.input += (u.input_tokens ?? 0) - cached
        sum.cacheRead += cached
        sum.cacheWrite += u.cache_write_input_tokens ?? 0
        sum.output += u.output_tokens ?? 0
        sum.messages++
      }
    }
  }
  return sum
}

// Codex 한도: 가장 최근 token_count 이벤트의 rate_limits (계정 전체 기준)
export function codexLimits() {
  const root = path.join(os.homedir(), '.codex', 'sessions')
  const files = [...walk(root, (p) => p.endsWith('.jsonl'))]
    .map((p) => ({ p, m: fs.statSync(p).mtimeMs }))
    .sort((a, b) => b.m - a.m)
    .slice(0, 30)
  let latest = null
  for (const { p } of files) {
    for (const d of jsonLines(p)) {
      const rl = d?.payload?.rate_limits
      if (d?.payload?.type === 'token_count' && rl && (!latest || d.timestamp > latest.ts)) {
        latest = { ts: d.timestamp, rl }
      }
    }
  }
  if (!latest) return null
  const out = { ts: latest.ts }
  for (const w of [latest.rl.primary, latest.rl.secondary]) {
    if (!w) continue
    if (w.window_minutes <= 300) out.fiveHour = w.used_percent
    else out.weekly = w.used_percent
  }
  return out
}

// Claude 한도: statusline.mjs가 남긴 기록 중 가장 최근 값 (계정 전체 기준)
export function claudeLimits(cwd) {
  const file = path.join(metricsDir(cwd), 'claude-limits.jsonl')
  if (!fs.existsSync(file)) return null
  const lines = fs.readFileSync(file, 'utf8').trim().split('\n')
  try {
    return JSON.parse(lines.at(-1))
  } catch {
    return null
  }
}

export const fmtInt = (n) => n.toLocaleString('en-US')

export function fmtKst(ms) {
  return new Date(ms + 9 * 3600_000).toISOString().slice(0, 16).replace('T', ' ')
}

export function fmtDuration(ms) {
  const m = Math.round(ms / 60_000)
  return m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`
}
