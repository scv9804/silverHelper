import { http, HttpResponse } from "msw";
import type { AuthToken, ChatAnswer, Detail, Profile, SavedProfile } from "../api";
import { benefits, codes, regions } from "./data";

const ok = <T>(data: T) =>
  HttpResponse.json({ success: true, data, error: null });
const page = <T>(content: T[], pageNumber: number, size: number) => ({
  content: content.slice(pageNumber * size, (pageNumber + 1) * size),
  page: pageNumber,
  size,
  totalElements: content.length,
  totalPages: Math.ceil(content.length / size),
});
let signedIn = false;
let mockName = "";
let profiles: SavedProfile[] = [];
let nextProfileId = 1;
const token = (): AuthToken => ({ accessToken: "mock-access-token", tokenType: "Bearer", expiresIn: 900, user: { id: 1, displayName: mockName, email: null, role: "USER" } });
const unauthorized = () => HttpResponse.json({ success: false, data: null, error: { code: "UNAUTHORIZED", message: "로그인이 필요합니다.", fieldErrors: [] } }, { status: 401 });
export const handlers = [
  http.post("/api/dev/login", async ({ request }) => { const body = await request.json() as { name: string }; mockName = body.name; signedIn = true; return ok(token()); }),
  http.post("/api/auth/refresh", () => signedIn ? ok(token()) : unauthorized()),
  http.post("/api/auth/exchange", () => unauthorized()),
  http.post("/api/auth/logout", () => { signedIn = false; return new HttpResponse(null, { status: 204 }); }),
  http.get("/api/me", () => signedIn ? ok(token().user) : unauthorized()),
  http.get("/api/me/profiles", () => signedIn ? ok(profiles) : unauthorized()),
  http.post("/api/me/profiles", async ({ request }) => { if (!signedIn) return unauthorized(); const body = await request.json() as Profile & { name: string }; const saved = { ...body, id: nextProfileId++ }; profiles = [...profiles, saved]; return HttpResponse.json({ success: true, data: saved, error: null }, { status: 201 }); }),
  http.put("/api/me/profiles/:id", async ({ request, params }) => { if (!signedIn) return unauthorized(); const body = await request.json() as Profile & { name: string }; const saved = { ...body, id: Number(params.id) }; profiles = profiles.map((item) => item.id === saved.id ? saved : item); return ok(saved); }),
  http.delete("/api/me/profiles/:id", ({ params }) => { if (!signedIn) return unauthorized(); profiles = profiles.filter((item) => item.id !== Number(params.id)); return new HttpResponse(null, { status: 204 }); }),
  http.post("/api/chat", async ({ request }) => { if (!signedIn) return unauthorized(); const { question } = await request.json() as { question: string }; const matching = benefits.find((item) => question.includes(item.name)); const answer: ChatAnswer = matching ? { answer: `가짜 응답: ${matching.name}의 공식 안내를 확인해 주세요.`, sources: [{ benefitId: matching.id, name: matching.name, sourceUrl: matching.sourceUrl, basis: matching.basis }], mock: true } : { answer: "모름", sources: [], mock: true }; return ok(answer); }),
  http.get("/api/regions", () => ok(regions)),
  http.get("/api/codes", () => ok(codes)),
  http.get("/api/benefits", ({ request }) => {
    const q = new URL(request.url).searchParams;
    const age = Number(q.get("age"));
    const ltc = Number(q.get("ltcGrade"));
    const income = Number(q.get("incomeMedianPct"));
    const filtered = benefits.filter(
      (b) =>
        (!q.has("age") ||
          b.eligibility.minAge === null ||
          age >= b.eligibility.minAge) &&
        (!q.has("ltcGrade") ||
          !b.eligibility.ltcRequired ||
          b.eligibility.ltcGrades?.includes(ltc)) &&
        (!q.has("incomeMedianPct") ||
          b.eligibility.incomeMaxMedianPct === null ||
          income <= b.eligibility.incomeMaxMedianPct) &&
        (!q.has("theme") ||
          b.tags.some((tag) =>
            q.get("theme")?.split(",").includes(tag.code),
          )) &&
        (!q.has("keyword") ||
          `${b.name} ${b.summary}`.includes(q.get("keyword")!)) &&
        (!q.has("ctpvName") ||
          b.region === null ||
          b.region.ctpvName === q.get("ctpvName")) &&
        (!q.has("sggName") ||
          b.region === null ||
          b.region.sggName === null ||
          b.region.sggName === q.get("sggName")),
    );
    return ok(
      page(filtered, Number(q.get("page") || 0), Number(q.get("size") || 20)),
    );
  }),
  http.get("/api/benefits/:id", ({ params }) => {
    const benefit = benefits.find((b) => b.id === Number(params.id));
    if (!benefit)
      return HttpResponse.json(
        {
          success: false,
          data: null,
          error: { code: "BENEFIT_NOT_FOUND", message: "혜택을 찾을 수 없습니다." },
        },
        { status: 404 },
      );
    const detail: Detail = {
      ...benefit,
      targetContent: benefit.eligibility.conditionsSummary,
      criteriaContent: benefit.eligibility.conditionsSummary,
      benefitContent: benefit.summary,
      applyContent: "신청 방법과 준비 서류는 공식 출처에서 확인해 주세요.",
      enforceStartDate: null,
      enforceEndDate: null,
      contact: "보건복지상담센터 129",
      references: {
        CONTACT: [{ name: "보건복지상담센터", value: "129" }],
        SITE: [{ name: "공식 안내", value: benefit.sourceUrl }],
        LAW: [],
        FORM: [],
        APPLY: [],
      },
      otherSources: [],
    };
    return ok(detail);
  }),
  http.post("/api/benefits/match", async ({ request }) => {
    const profile = (await request.json()) as Profile;
    const matches = benefits.flatMap((benefit) => {
      const e = benefit.eligibility;
      if (benefit.region && benefit.region.ctpvName !== profile.ctpvName) return [];
      if (benefit.region?.sggName && benefit.region.sggName !== profile.sggName) return [];
      if (e.minAge !== null && profile.age < e.minAge) return [];
      if (e.ltcRequired && profile.hasNoLtcGrade) return [];
      if (
        e.ltcRequired &&
        profile.ltcGrade !== null &&
        !e.ltcGrades?.includes(profile.ltcGrade)
      )
        return [];
      if (
        e.incomeMaxMedianPct !== null &&
        profile.incomeMedianPct !== null &&
        profile.incomeMedianPct > e.incomeMaxMedianPct
      )
        return [];
      if (e.requiresSingleHousehold && profile.livingAlone === false) return [];
      const checkItems = [
        ...(e.ltcRequired && profile.ltcGrade === null
          ? ["장기요양 등급을 확인하세요."]
          : []),
        ...(e.incomeMaxMedianPct !== null && profile.incomeMedianPct === null
          ? ["가구의 중위소득 비율을 확인하세요."]
          : []),
        ...(e.requiresSingleHousehold && profile.livingAlone === null
          ? ["독거 여부를 확인하세요."]
          : []),
      ];
      return [
        {
          benefit,
          matchLevel: checkItems.length
            ? ("CHECK_NEEDED" as const)
            : ("ELIGIBLE" as const),
          checkItems,
        },
      ];
    });
    const url = new URL(request.url);
    return ok({
      ...page(
        matches,
        Number(url.searchParams.get("page") || 0),
        Number(url.searchParams.get("size") || 20),
      ),
      summary: {
        eligibleCount: matches.filter((m) => m.matchLevel === "ELIGIBLE")
          .length,
        checkNeededCount: matches.filter((m) => m.matchLevel === "CHECK_NEEDED")
          .length,
      },
    });
  }),
];
