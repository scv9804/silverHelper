import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api, type MatchPage } from "../api";
import { useProfile } from "../store";
import { StateView } from "../components/StateView";
import { Card } from "../components/BenefitCard";
import { note } from "../note";
import { pageSizes, usePageSize } from "../page-size";
export function ResultsPage() {
  const profile = useProfile((state) => state.profile);
  const [page, setPage] = useState(0);
  const { size, changeSize } = usePageSize();
  const { data, isPending, error } = useQuery({
    queryKey: ["match", profile, page, size],
    queryFn: () =>
      api<MatchPage>(`/api/benefits/match?page=${page}&size=${size}`, {
        method: "POST",
        body: JSON.stringify(profile),
      }),
    enabled: !!profile.ctpvName,
  });
  if (!profile.ctpvName)
    return (
      <div className="panel">
        <h1>먼저 거주 지역을 입력해 주세요</h1>
        <Link to="/">정보 입력하기</Link>
      </div>
    );
  return (
    <section>
      <div className="page-head">
        <span className="eyebrow">맞춤 혜택</span>
        <h1>부모님께 맞는 혜택</h1>
        <p>{note}</p>
      </div>
      <StateView
        loading={isPending}
        error={error}
        empty={data?.content.length === 0}
      />
      <label className="page-size">몇 개씩 보기<select value={size} onChange={(event) => { changeSize(Number(event.target.value)); setPage(0); }}>{pageSizes.map((count) => <option key={count} value={count}>{count}개</option>)}</select></label>
      {data && (
        <>
          <div className="summary">
            <span>
              받을 수 있어요 <strong>{data.summary.eligibleCount}건</strong>
            </span>
            <span>
              확인이 필요해요 <strong>{data.summary.checkNeededCount}건</strong>
            </span>
          </div>
          {(["ELIGIBLE", "CHECK_NEEDED"] as const).map((level) => (
            <div key={level} className="section-block">
              <h2>
                {level === "ELIGIBLE" ? "받을 수 있어요" : "확인이 필요해요"}
              </h2>
              <div className="cards">
                {data.content
                  .filter((item) => item.matchLevel === level)
                  .map((item) => (
                    <Card
                      key={item.benefit.id}
                      benefit={item.benefit}
                      checkItems={item.checkItems}
                    />
                  ))}
              </div>
            </div>
          ))}
          <div className="pager" aria-label="매칭 결과 페이지 이동">
            <button disabled={data.page === 0} onClick={() => setPage(data.page - 1)}>
              ← 이전
            </button>
            <span>{data.page + 1} / {Math.max(1, data.totalPages)}</span>
            <button
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => setPage(data.page + 1)}
            >
              다음 →
            </button>
          </div>
        </>
      )}
      <Link className="secondary" to="/">
        정보 수정하기
      </Link>
    </section>
  );
}
