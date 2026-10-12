export type ApiResponse<T> = { success: boolean; data: T | null; error: { code: string; message: string } | null }
export type Basis = { lastCollectedAt: string; sourceModifiedAt: string | null; baseYear: number | null; updatePending: boolean }
export type Eligibility = { minAge: number | null; maxAge: number | null; ltcRequired: boolean | null; ltcGrades: number[] | null; incomeMaxMedianPct: number | null; requiresBasicLivelihood: boolean | null; requiresNearPoverty: boolean | null; requiresBasicPension: boolean | null; requiresDisability: boolean | null; requiresVeteran: boolean | null; requiresSingleHousehold: boolean | null; conditionsSummary: string | null }
export type Benefit = { id: number; groupId: number; name: string; summary: string | null; source: 'NATIONAL' | 'LOCAL' | 'GOV24'; sourceUrl: string; agencyName: string | null; region: { ctpvName: string; sggName: string | null } | null; provisionType: string | null; supportCycle: string | null; onlineApply: boolean | null; tags: { type: 'LIFE' | 'HOUSEHOLD' | 'THEME'; code: string; name: string }[]; eligibility: Eligibility; basis: Basis }
export type Detail = Benefit & { targetContent: string | null; criteriaContent: string | null; benefitContent: string | null; applyContent: string | null; enforceStartDate: string | null; enforceEndDate: string | null; contact: string | null; references: Record<'CONTACT' | 'SITE' | 'LAW' | 'FORM' | 'APPLY', { name: string | null; value: string | null }[]>; otherSources: { id: number; source: Benefit['source']; name: string; sourceUrl: string }[] }
export type Profile = { age: number; ctpvName: string; sggName: string | null; ltcGrade: number | null; hasNoLtcGrade: boolean | null; incomeMedianPct: number | null; basicLivelihood: boolean | null; nearPoverty: boolean | null; basicPension: boolean | null; disability: boolean | null; veteran: boolean | null; livingAlone: boolean | null }
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export type Match = { benefit: Benefit; matchLevel: 'ELIGIBLE' | 'CHECK_NEEDED'; checkItems: string[] }
export type MatchPage = Page<Match> & { summary: { eligibleCount: number; checkNeededCount: number } }
export type Codes = Record<'LIFE' | 'HOUSEHOLD' | 'THEME' | 'LTC_GRADE', { code: string; name: string }[]>
export type Region = { ctpvName: string; sggNames: string[] }

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...options?.headers } })
  let result: ApiResponse<T>
  try { result = await response.json() as ApiResponse<T> } catch { throw new Error('서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해 주세요.') }
  if (!response.ok || !result.success || result.data === null) throw new Error(result.error?.message || '요청을 처리하지 못했습니다.')
  return result.data
}
