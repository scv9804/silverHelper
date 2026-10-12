import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api, type Benefit, type Codes, type Page, type Region } from "../api";
import { StateView } from "../components/StateView";
import { Card } from "../components/BenefitCard";
export function BenefitsPage() {
  const [params, setParams] = useSearchParams();
  const [draft, setDraft] = useState(params.get("keyword") || "");
  const [ageDraft, setAgeDraft] = useState(params.get("age") || "");
  const [incomeDraft, setIncomeDraft] = useState(params.get("incomeMedianPct") || "");
  const { data: codes } = useQuery({
    queryKey: ["codes"],
    queryFn: () => api<Codes>("/api/codes"),
  });
  const { data: regions } = useQuery({
    queryKey: ["regions"],
    queryFn: () => api<Region[]>("/api/regions"),
  });
  const { data, isPending, error } = useQuery({
    queryKey: ["benefits", params.toString()],
    queryFn: () => api<Page<Benefit>>(`/api/benefits?${params.toString()}`),
  });
  const change = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete("page");
    setParams(next);
  };
  return (
    <section>
      <div className="page-head">
        <span className="eyebrow">혜택 살펴보기</span>
        <h1>전체 혜택 목록</h1>
        <p>조건을 선택해 필요한 혜택을 찾아보세요.</p>
      </div>
      <form
        className="filters panel"
        onSubmit={(event) => {
          event.preventDefault();
          change("keyword", draft.trim());
        }}
      >
        <label>
          검색어
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="예: 장기요양"
            maxLength={50}
          />
        </label>
        <button className="secondary" type="submit">
          검색
        </button>
        <label>
          나이
          <input
            type="number"
            min="0"
            max="120"
            value={ageDraft}
            onChange={(e) => setAgeDraft(e.target.value)}
            onBlur={() => change("age", ageDraft)}
            placeholder="전체"
          />
        </label>
        <label>
          시도
          <select
            value={params.get("ctpvName") || ""}
            onChange={(e) => change("ctpvName", e.target.value)}
          >
            <option value="">전국</option>
            {[...new Set(regions?.map((r) => r.ctpvName) || [])].map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </label>
        <label>
          시군구
          <select
            value={params.get("sggName") || ""}
            onChange={(e) => change("sggName", e.target.value)}
          >
            <option value="">전체</option>
            {regions
              ?.find((r) => r.ctpvName === params.get("ctpvName"))
              ?.sggNames.map((name) => (
                <option key={name}>{name}</option>
              ))}
          </select>
        </label>
        <label>
          장기요양 등급
          <select
            value={params.get("ltcGrade") || ""}
            onChange={(e) => change("ltcGrade", e.target.value)}
          >
            <option value="">전체</option>
            {codes?.LTC_GRADE.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          중위소득 비율 (%)
          <input
            type="number"
            min="0"
            max="1000"
            value={incomeDraft}
            onChange={(e) => setIncomeDraft(e.target.value)}
            onBlur={() => change("incomeMedianPct", incomeDraft)}
            placeholder="전체"
          />
        </label>
        <label>
          분야
          <select
            value={params.get("theme") || ""}
            onChange={(e) => change("theme", e.target.value)}
          >
            <option value="">전체</option>
            {codes?.THEME.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </form>
      <StateView
        loading={isPending}
        error={error}
        empty={data?.content.length === 0}
      />
      {data && (
        <>
          <p className="count">총 {data.totalElements}건</p>
          <div className="cards">
            {data.content.map((b) => (
              <Card key={b.id} benefit={b} />
            ))}
          </div>
          <div className="pager">
            <button
              disabled={data.page === 0}
              onClick={() => {
                const next = new URLSearchParams(params);
                next.set("page", String(data.page - 1));
                setParams(next);
              }}
            >
              ← 이전
            </button>
            <span>
              {data.page + 1} / {Math.max(1, data.totalPages)}
            </span>
            <button
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => {
                const next = new URLSearchParams(params);
                next.set("page", String(data.page + 1));
                setParams(next);
              }}
            >
              다음 →
            </button>
          </div>
        </>
      )}
    </section>
  );
}
