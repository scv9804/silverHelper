import { useState } from "react";
import { Link } from "react-router-dom";
import { api, type ChatAnswer } from "../api";
import { useAuth } from "../auth-context";

export function ChatPage() {
  const { user, ready } = useAuth();
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<ChatAnswer | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (!ready) return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!user) return <div className="panel"><h1>로그인이 필요합니다</h1><Link to="/login">로그인하기</Link></div>;
  const ask = async () => {
    setBusy(true); setError(""); setAnswer(null);
    try { setAnswer(await api<ChatAnswer>("/api/chat", { method: "POST", body: JSON.stringify({ question: question.trim() }) })); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "답변을 받지 못했습니다."); }
    finally { setBusy(false); }
  };
  return <section className="narrow"><div className="page-head"><span className="eyebrow">혜택 상담</span><h1>복지 혜택 챗봇</h1><p>공개된 혜택 정보를 근거로 답변합니다. 개인정보는 질문에 적지 마세요.</p></div>
    <form className="panel" onSubmit={(event) => { event.preventDefault(); void ask(); }}><label>질문<textarea required minLength={1} maxLength={1000} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="예: 서울에 사는 75세 부모님이 받을 수 있는 돌봄 혜택은?" /></label><button className="primary" disabled={busy || !question.trim()}>{busy ? "답변 확인 중…" : "질문하기"}</button></form>
    {error && <p className="notice error" role="alert">{error}</p>}
    {answer && <article className="panel section-block" aria-live="polite"><h2>답변 {answer.mock && <span className="mock-badge">가짜 응답 · 실제 정책 안내 아님</span>}</h2><p className="answer">{answer.sources.length === 0 ? "모름" : answer.answer}</p><h3>근거</h3>{answer.sources.length === 0 ? <p>표시할 근거가 없습니다.</p> : <ul className="source-list">{answer.sources.map((source) => <li key={source.benefitId}><Link to={`/benefits/${source.benefitId}`}>{source.name}</Link> · <a href={source.sourceUrl} target="_blank" rel="noreferrer">공식 출처 ↗</a><small>기준일: {source.basis.lastCollectedAt.slice(0, 10)}{source.basis.updatePending && " · 갱신 확인 중"}</small></li>)}</ul>}<p className="muted">자격과 신청 방법은 공식 출처에서 다시 확인하세요.</p></article>}
  </section>;
}
