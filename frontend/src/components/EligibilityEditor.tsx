import type { Eligibility } from "../api";

const booleanFields = [
  ["장기요양 등급 필요", "ltcRequired"],
  ["기초생활수급 필요", "requiresBasicLivelihood"],
  ["차상위계층 필요", "requiresNearPoverty"],
  ["기초연금 수급 필요", "requiresBasicPension"],
  ["등록 장애 필요", "requiresDisability"],
  ["보훈 대상 필요", "requiresVeteran"],
  ["독거 필요", "requiresSingleHousehold"],
] as const;

export function EligibilityEditor({ value, onChange }: { value: Eligibility; onChange: (value: Eligibility) => void }) {
  const numeric = (key: "minAge" | "maxAge" | "incomeMaxMedianPct", label: string, max: number) => (
    <label key={key}>{label}<input type="number" min={0} max={max} value={value[key] ?? ""} placeholder="조건 없음" onChange={(event) => onChange({ ...value, [key]: event.target.value === "" ? null : Number(event.target.value) })} /></label>
  );
  return <div className="manual-fields">
    <p className="muted">공식 원문을 확인해 조건을 입력하세요. 언급되지 않은 조건은 비워 두세요.</p>
    <div className="field-row">{numeric("minAge", "최소 나이", 120)}{numeric("maxAge", "최대 나이", 120)}</div>
    <div className="field-row">{numeric("incomeMaxMedianPct", "중위소득 상한 (%)", 1000)}</div>
    <div className="questions">{booleanFields.map(([label, key]) => <label key={key}>{label}<select value={value[key] === null ? "" : String(value[key])} onChange={(event) => onChange({ ...value, [key]: event.target.value === "" ? null : event.target.value === "true", ...(key === "ltcRequired" && event.target.value !== "true" ? { ltcGrades: null } : {}) })}><option value="">조건 없음</option><option value="true">예</option><option value="false">아니오</option></select></label>)}</div>
    <fieldset disabled={value.ltcRequired !== true}><legend>해당 장기요양 등급</legend><div className="choices">{[1, 2, 3, 4, 5, 6].map((grade) => <label key={grade}><input type="checkbox" checked={value.ltcGrades?.includes(grade) ?? false} onChange={(event) => { const grades = new Set(value.ltcGrades ?? []); if (event.target.checked) grades.add(grade); else grades.delete(grade); onChange({ ...value, ltcGrades: grades.size ? [...grades].sort() : null }); }} />{grade === 6 ? "인지지원등급" : `${grade}등급`}</label>)}</div></fieldset>
    <label>조건 요약<textarea maxLength={300} value={value.conditionsSummary ?? ""} placeholder="원문에 근거한 조건을 적어 주세요." onChange={(event) => onChange({ ...value, conditionsSummary: event.target.value || null })} /></label>
  </div>;
}
