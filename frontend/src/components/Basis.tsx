import type { Benefit } from "../api";
export function Basis({ benefit }: { benefit: Benefit }) {
  return (
    <div className="basis">
      <a href={benefit.sourceUrl} target="_blank" rel="noopener noreferrer">
        공식 출처 확인 ↗
      </a>
      <span>
        기준일{" "}
        {new Date(benefit.basis.lastCollectedAt).toLocaleDateString("ko-KR")}
      </span>
      {benefit.basis.updatePending && <strong>정보 갱신 확인 중</strong>}
    </div>
  );
}
