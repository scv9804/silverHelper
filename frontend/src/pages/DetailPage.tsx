import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api, type Detail } from "../api";
import { Basis } from "../components/Basis";
import { StateView } from "../components/StateView";
import { note } from "../note";
import { SaveBenefit } from "../components/SaveBenefit";
export function DetailPage() {
  const { id } = useParams();
  const { data, isPending, error } = useQuery({
    queryKey: ["detail", id],
    queryFn: () => api<Detail>(`/api/benefits/${id}`),
  });
  const fields = [
    ["지원 대상", data?.targetContent],
    ["선정 기준", data?.criteriaContent],
    ["지원 내용", data?.benefitContent],
    ["신청 방법", data?.applyContent],
  ];
  const labels = {
    CONTACT: "문의처",
    SITE: "관련 사이트",
    FORM: "서식",
    LAW: "근거 법령",
    APPLY: "신청",
  };
  return (
    <section>
      <StateView loading={isPending} error={error} />
      {data && (
        <>
          <div className="page-head">
            <span className="eyebrow">
              {data.tags.map((t) => t.name).join(" · ")}
            </span>
            <h1>{data.name}</h1>
            <p>{data.summary}</p>
            <Basis benefit={data} />
            <SaveBenefit id={data.id} />
            <p className="notice">{note}</p>
          </div>
          <div className="detail-grid">
            <div>
              {fields.map(([title, value]) => (
                <section className="panel detail-section" key={title}>
                  <h2>{title}</h2>
                  <p>{value || "공식 출처에서 확인해 주세요."}</p>
                </section>
              ))}
            </div>
            <aside className="panel">
              <h2>문의와 자료</h2>
              <p>{data.contact || "공식 출처에서 확인해 주세요."}</p>
              {(["CONTACT", "SITE", "FORM", "LAW", "APPLY"] as const).map(
                (key) =>
                  data.references[key].length > 0 && (
                    <div key={key} className="reference">
                      <h3>{labels[key]}</h3>
                      {data.references[key].map((ref, i) => (
                        <p key={i}>
                          {ref.value && /^https?:\/\//.test(ref.value) ? (
                            <a
                              href={ref.value}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              {ref.name || ref.value} ↗
                            </a>
                          ) : (
                            `${ref.name || ""} ${ref.value || ""}`
                          )}
                        </p>
                      ))}
                    </div>
                  ),
              )}
              {data.otherSources.length > 0 && (
                <div className="reference">
                  <h3>다른 출처</h3>
                  {data.otherSources.map((source) => (
                    <p key={source.id}>
                      <a
                        href={source.sourceUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        {source.name} ↗
                      </a>
                    </p>
                  ))}
                </div>
              )}
            </aside>
          </div>
        </>
      )}
    </section>
  );
}
