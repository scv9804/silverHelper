import { Link } from "react-router-dom";
import type { Benefit } from "../api";
import { Basis } from "./Basis";
export function Card({
  benefit,
  checkItems,
}: {
  benefit: Benefit;
  checkItems?: string[];
}) {
  return (
    <article className="card">
      <div className="eyebrow">
        {benefit.tags.map((tag) => tag.name).join(" · ")} ·{" "}
        {benefit.region?.ctpvName || "전국"}
      </div>
      <h3>
        <Link to={`/benefits/${benefit.id}`}>{benefit.name}</Link>
      </h3>
      <p>{benefit.summary}</p>
      {checkItems && checkItems.length > 0 && (
        <div className="check">
          <strong>확인할 항목</strong>
          <ul>
            {checkItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}
      <Basis benefit={benefit} />
      <Link className="text-link" to={`/benefits/${benefit.id}`}>
        자세히 보기 →
      </Link>
    </article>
  );
}
