import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, type Page, type ReviewDetail, type ReviewSummary } from "../api";
import { useAuth } from "../auth-context";
import { StateView } from "../components/StateView";

const reasons: Record<ReviewSummary["reason"], string> = { MODEL_MISMATCH: "모델 결과 불일치", SCHEMA_INVALID: "형식 오류", STRUCTURED_MISMATCH: "구조화 값 불일치", GROUP_CANDIDATE: "중복 후보", SOURCE_REMOVED: "원천 삭제", OTHER: "기타" };

export function AdminReviewsPage() {
  const { user, ready } = useAuth();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [baseExtractionId, setBaseExtractionId] = useState("");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const client = useQueryClient();
  const list = useQuery({ queryKey: ["reviews", page], queryFn: () => api<Page<ReviewSummary>>(`/api/admin/reviews?status=PENDING&page=${page}&size=20`), enabled: user?.role === "ADMIN" });
  const detail = useQuery({ queryKey: ["review", selected], queryFn: () => api<ReviewDetail>(`/api/admin/reviews/${selected}`), enabled: user?.role === "ADMIN" && selected !== null });
  if (!ready) return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!user) return <div className="panel"><h1>로그인이 필요합니다</h1><Link to="/login">로그인하기</Link></div>;
  if (user.role !== "ADMIN") return <div className="panel"><h1>관리자 권한이 필요합니다</h1><p>검토 대기열은 ADMIN 회원만 볼 수 있습니다.</p></div>;
  const resolve = async (action: "approve" | "reject") => {
    if (selected === null) return;
    if (action === "reject" && !note.trim()) { setMessage("반려 사유를 입력해 주세요."); return; }
    if (action === "approve" && detail.data?.reason !== "GROUP_CANDIDATE" && !baseExtractionId) { setMessage("승인 기준 추출 결과를 선택해 주세요."); return; }
    setBusy(true); setMessage("");
    try {
      await api<ReviewSummary>(`/api/admin/reviews/${selected}/${action}`, { method: "POST", body: JSON.stringify(action === "approve" ? { baseExtractionId: baseExtractionId ? Number(baseExtractionId) : null, note: note.trim() || null } : { note: note.trim() }) });
      setMessage(action === "approve" ? "승인했습니다." : "반려했습니다.");
      setSelected(null); setBaseExtractionId(""); setNote("");
      await client.invalidateQueries({ queryKey: ["reviews"] });
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "처리하지 못했습니다."); }
    finally { setBusy(false); }
  };
  return <section><div className="page-head"><span className="eyebrow">관리자</span><h1>검토 대기열</h1><p>원문과 모델 결과를 확인한 뒤 승인하거나 반려하세요.</p></div>
    {message && <p role="status" className="notice">{message}</p>}
    <StateView loading={list.isPending} error={list.error} empty={list.data?.content.length === 0} />
    <div className="review-grid"><div className="stack">{list.data?.content.map((review) => <button key={review.id} className={`review-item ${selected === review.id ? "selected" : ""}`} onClick={() => { setSelected(review.id); setBaseExtractionId(""); setNote(""); setMessage(""); }}><strong>{review.benefitName}</strong><span>{reasons[review.reason]} · {new Date(review.createdAt).toLocaleString("ko-KR")}</span></button>)}
      {list.data && <div className="pager"><button disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} / {Math.max(1, list.data.totalPages)}</span><button disabled={page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>다음</button></div>}</div>
      <div className="panel">{selected === null ? <p>왼쪽에서 검토 항목을 선택하세요.</p> : <><StateView loading={detail.isPending} error={detail.error} />{detail.data && <><h2>{detail.data.benefitName}</h2><p><a href={detail.data.sourceUrl} target="_blank" rel="noreferrer">공식 원문 보기 ↗</a></p><p>사유: {reasons[detail.data.reason]}</p>
        {detail.data.groupCandidate && <p className="notice">중복 후보: {detail.data.groupCandidate.targetName} (#{detail.data.groupCandidate.targetBenefitId})</p>}
        <h3>원문</h3>{Object.entries(detail.data.sourceText).map(([key, value]) => <div key={key} className="review-section"><strong>{key}</strong><p>{value}</p></div>)}
        <h3>모델 추출 결과</h3>{detail.data.extractions.map((item) => <div className="review-section" key={item.id}><strong>{item.provider} · {item.model} {item.schemaValid ? "" : "(형식 오류)"}</strong>{item.errorMessage && <p className="error">{item.errorMessage}</p>}<pre>{JSON.stringify(item.result, null, 2)}</pre></div>)}
        <h3>서로 다른 항목</h3>{detail.data.differences.length ? <div className="table-scroll"><table><thead><tr><th>항목</th><th>Claude</th><th>GPT</th><th>구조화 값</th></tr></thead><tbody>{detail.data.differences.map((diff) => <tr key={diff.field}><th>{diff.field}</th><td>{JSON.stringify(diff.claude)}</td><td>{JSON.stringify(diff.gpt)}</td><td>{JSON.stringify(diff.structured)}</td></tr>)}</tbody></table></div> : <p>차이 없음</p>}
        {detail.data.currentEligibility && <details><summary>현재 공개 조건</summary><pre>{JSON.stringify(detail.data.currentEligibility, null, 2)}</pre></details>}
        {detail.data.reason !== "GROUP_CANDIDATE" && <label>승인 기준 추출 결과<select value={baseExtractionId} onChange={(event) => setBaseExtractionId(event.target.value)}><option value="">선택하세요</option>{detail.data.extractions.filter((item) => item.schemaValid).map((item) => <option key={item.id} value={item.id}>{item.provider} · {item.model}</option>)}</select></label>}
        <label>처리 메모<textarea maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} placeholder="반려 시 필수" /></label><div className="actions"><button className="primary" disabled={busy || (detail.data.reason !== "GROUP_CANDIDATE" && !baseExtractionId)} onClick={() => void resolve("approve")}>승인</button><button className="secondary" disabled={busy || !note.trim()} onClick={() => void resolve("reject")}>반려</button></div>
      </>}</>}</div></div>
  </section>;
}
