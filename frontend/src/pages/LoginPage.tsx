import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, type AuthToken } from "../api";
import { useAuth } from "../auth-context";

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const local = import.meta.env.DEV;
  const signIn = async () => {
    setBusy(true); setError("");
    try { login(await api<AuthToken>("/api/dev/login", { method: "POST", body: JSON.stringify({ name: name.trim() }) })); navigate("/me"); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "로그인에 실패했습니다."); }
    finally { setBusy(false); }
  };
  if (user) return <div className="panel"><h1>로그인되었습니다</h1><Link to="/me">마이페이지로 이동</Link></div>;
  return <section className="narrow panel"><h1>로그인</h1><p>부모님 프로필을 저장하고 이어서 혜택을 찾아보세요.</p>
    <div className="stack"><a className="secondary" href="/oauth2/authorization/kakao">카카오로 로그인</a><a className="secondary" href="/oauth2/authorization/google">Google로 로그인</a></div>
    {local && <form className="section-block" onSubmit={(event) => { event.preventDefault(); void signIn(); }}><h2>개발용 로그인</h2><p className="muted">로컬 서버에서만 사용할 수 있습니다. USER 권한으로 로그인합니다.</p><label>이름<input required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} /></label><button className="primary" disabled={busy || !name.trim()}>{busy ? "로그인 중…" : "개발용 로그인"}</button></form>}
    {error && <p role="alert" className="notice error">{error}</p>}
  </section>;
}

export function AuthCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();
  const [error, setError] = useState("");
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const code = params.get("code");
    if (!code) return;
    window.history.replaceState(null, "", "/auth/callback");
    api<AuthToken>("/api/auth/exchange", { method: "POST", body: JSON.stringify({ code }) })
      .then((token) => { login(token); navigate("/me", { replace: true }); })
      .catch((cause) => setError(cause instanceof Error ? cause.message : "로그인에 실패했습니다."));
  }, [params, login, navigate]);
  return <div className="panel"><h1>로그인 처리 중</h1>{(error || !params.get("code")) ? <p role="alert" className="notice error">{error || "로그인 코드가 없습니다."} <Link to="/login">다시 로그인</Link></p> : <p>잠시만 기다려 주세요.</p>}</div>;
}
