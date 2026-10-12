// Claude Code 상태줄: 한도 사용률을 표시하고 .metrics/claude-limits.jsonl에 기록한다.
// Claude Code가 stdin으로 넘겨주는 JSON의 rate_limits(five_hour, seven_day)를 사용.
import fs from 'node:fs'
import path from 'node:path'
import { metricsDir } from './lib.mjs'

let raw = ''
process.stdin.on('data', (c) => (raw += c))
process.stdin.on('end', () => {
  let d = {}
  try {
    d = JSON.parse(raw)
  } catch {
    // 입력이 없으면 기본 표시만
  }
  const five = d.rate_limits?.five_hour?.used_percentage
  const week = d.rate_limits?.seven_day?.used_percentage
  const ctx = d.context_window?.used_percentage

  if (five !== undefined || week !== undefined) {
    try {
      const file = path.join(metricsDir(d.workspace?.current_dir ?? d.cwd), 'claude-limits.jsonl')
      let last = null
      if (fs.existsSync(file)) {
        const lines = fs.readFileSync(file, 'utf8').trim().split('\n')
        last = JSON.parse(lines.at(-1) || 'null')
      }
      // 값이 바뀌었을 때만 기록
      if (!last || last.fiveHour !== five || last.weekly !== week) {
        const row = { ts: new Date().toISOString(), fiveHour: five, weekly: week, session: d.session_id }
        fs.appendFileSync(file, JSON.stringify(row) + '\n')
      }
    } catch {
      // 기록 실패가 상태줄 표시를 막지 않도록 무시
    }
  }

  const pct = (v) => (v === undefined ? '-' : `${Math.round(v)}%`)
  const model = d.model?.display_name ?? 'Claude'
  console.log(`${model} | 5h ${pct(five)} | 주간 ${pct(week)} | ctx ${pct(ctx)}`)
})
