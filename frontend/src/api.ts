export type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
};
export type Basis = {
  lastCollectedAt: string;
  sourceModifiedAt: string | null;
  baseYear: number | null;
  updatePending: boolean;
};
export type Eligibility = {
  minAge: number | null;
  maxAge: number | null;
  ltcRequired: boolean | null;
  ltcGrades: number[] | null;
  incomeMaxMedianPct: number | null;
  requiresBasicLivelihood: boolean | null;
  requiresNearPoverty: boolean | null;
  requiresBasicPension: boolean | null;
  requiresDisability: boolean | null;
  requiresVeteran: boolean | null;
  requiresSingleHousehold: boolean | null;
  conditionsSummary: string | null;
};
export type Benefit = {
  id: number;
  groupId: number;
  name: string;
  summary: string | null;
  source: "NATIONAL" | "LOCAL" | "GOV24";
  sourceUrl: string;
  agencyName: string | null;
  region: { ctpvName: string; sggName: string | null } | null;
  provisionType: string | null;
  supportCycle: string | null;
  onlineApply: boolean | null;
  tags: { type: "LIFE" | "HOUSEHOLD" | "THEME"; code: string; name: string }[];
  eligibility: Eligibility;
  basis: Basis;
};
export type Detail = Benefit & {
  targetContent: string | null;
  criteriaContent: string | null;
  benefitContent: string | null;
  applyContent: string | null;
  enforceStartDate: string | null;
  enforceEndDate: string | null;
  contact: string | null;
  references: Record<
    "CONTACT" | "SITE" | "LAW" | "FORM" | "APPLY",
    { name: string | null; value: string | null }[]
  >;
  otherSources: {
    id: number;
    source: Benefit["source"];
    name: string;
    sourceUrl: string;
  }[];
};
export type Profile = {
  age: number;
  ctpvName: string;
  sggName: string | null;
  ltcGrade: number | null;
  hasNoLtcGrade: boolean | null;
  incomeMedianPct: number | null;
  basicLivelihood: boolean | null;
  nearPoverty: boolean | null;
  basicPension: boolean | null;
  disability: boolean | null;
  veteran: boolean | null;
  livingAlone: boolean | null;
};
export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type Match = {
  benefit: Benefit;
  matchLevel: "ELIGIBLE" | "CHECK_NEEDED";
  checkItems: string[];
};
export type MatchPage = Page<Match> & {
  summary: { eligibleCount: number; checkNeededCount: number };
};
export type Codes = Record<
  "LIFE" | "HOUSEHOLD" | "THEME" | "LTC_GRADE",
  { code: string; name: string }[]
>;
export type Region = { ctpvName: string; sggNames: string[] };

export type Me = { id: number; displayName: string; email: string | null; role: "USER" | "ADMIN" };
export type AuthToken = { accessToken: string; tokenType: "Bearer"; expiresIn: number; user: Me };
export type SavedProfile = Profile & { id: number; name: string };
export type NotificationSettings = { emailEnabled: boolean; newBenefitEnabled: boolean; deadlineEnabled: boolean; deadlineDays: number };
export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED" | "RESOLVED";
export type ReviewReason = "MODEL_MISMATCH" | "SCHEMA_INVALID" | "STRUCTURED_MISMATCH" | "GROUP_CANDIDATE" | "SOURCE_REMOVED" | "OTHER";
export type ReviewSummary = { id: number; benefitId: number; benefitName: string; source: Benefit["source"]; reason: ReviewReason; status: ReviewStatus; createdAt: string; resolvedAt: string | null; resolvedBy: string | null };
export type Extraction = { id: number; provider: "CLAUDE" | "GPT"; model: string; promptVersion: string; schemaValid: boolean; result: Record<string, unknown> | null; errorMessage: string | null; createdAt: string };
export type ReviewDetail = ReviewSummary & { sourceUrl: string; sourceText: Record<string, string>; currentEligibility: Eligibility | null; extractions: Extraction[]; differences: { field: string; claude: unknown; gpt: unknown; structured: unknown }[]; groupCandidate: { targetBenefitId: number; targetName: string; targetSource: Benefit["source"]; method: "NAME_EXACT" | "NAME_SIMILAR" | "AI"; score: number | null } | null; resolutionNote: string | null };
export type ChatAnswer = { answer: string; sources: { benefitId: number; name: string; sourceUrl: string; basis: Basis }[]; mock: boolean };

let accessToken: string | null = null;
let accessVersion = 0;
export const SESSION_EXPIRED_EVENT = "silverhelper-session-expired";
export function setAccessToken(token: string | null) { accessToken = token; accessVersion++; }
class StaleRefreshError extends Error {}
function notifySessionExpired() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;
  let result: ApiResponse<T>;
  try { result = (await response.json()) as ApiResponse<T>; }
  catch { throw new Error("서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해 주세요."); }
  if (!response.ok || !result.success || result.data === null)
    throw new Error(result.error?.message || "요청을 처리하지 못했습니다.");
  return result.data;
}

let refreshInFlight: Promise<AuthToken> | null = null;
export function refreshToken(): Promise<AuthToken> {
  if (!refreshInFlight) {
    const version = accessVersion;
    const perform = () => fetch("/api/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then((response) => parseResponse<AuthToken>(response))
      .then((token) => {
        if (version !== accessVersion) throw new StaleRefreshError("Session changed during refresh");
        setAccessToken(token.accessToken);
        return token;
      });
    // The refresh cookie rotates once. Serialize refreshes across open tabs.
    const locks = typeof navigator === "undefined" ? undefined : navigator.locks;
    refreshInFlight = (locks ? locks.request("silverhelper-refresh", perform) : perform())
      .finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const request = () => fetch(path, {
    ...options,
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}), ...options?.headers },
  });
  let response = await request();
  if (response.status === 401 && !path.startsWith("/api/auth/") && !path.startsWith("/api/dev/")) {
    let refreshedVersion: number | null = null;
    try { await refreshToken(); refreshedVersion = accessVersion; response = await request(); }
    catch (error) {
      if (!(error instanceof StaleRefreshError)) {
        setAccessToken(null);
        notifySessionExpired();
      }
    }
    if (refreshedVersion === accessVersion && response.status === 401) {
      setAccessToken(null);
      notifySessionExpired();
    }
  }
  return parseResponse<T>(response);
}
