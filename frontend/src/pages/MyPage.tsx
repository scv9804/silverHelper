import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, type SavedProfile } from "../api";
import { useAuth } from "../auth-context";
import { useProfile } from "../store";
import { StateView } from "../components/StateView";

export function MyPage() {
  const { user, ready } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { profile, update } = useProfile();
  const [name, setName] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const { data, isPending, error } = useQuery({ queryKey: ["my-profiles", user?.id], queryFn: () => api<SavedProfile[]>("/api/me/profiles"), enabled: !!user });
  if (!ready) return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!user) return <div className="panel"><h1>로그인이 필요합니다</h1><Link to="/login">로그인하기</Link></div>;
  const save = async () => {
    if (!name.trim() || !profile.ctpvName) { setMessage("이름과 거주 지역을 입력해 주세요."); return; }
    setBusy(true); setMessage("");
    try {
      await api<SavedProfile>(editingId === null ? "/api/me/profiles" : `/api/me/profiles/${editingId}`, { method: editingId === null ? "POST" : "PUT", body: JSON.stringify({ ...profile, name: name.trim() }) });
      await queryClient.invalidateQueries({ queryKey: ["my-profiles"] });
      setMessage("프로필을 저장했습니다."); setEditingId(null); setName("");
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "저장하지 못했습니다."); }
    finally { setBusy(false); }
  };
  const remove = async (id: number) => {
    if (!window.confirm("이 프로필을 삭제할까요?")) return;
    setBusy(true); setMessage("");
    try { await api<void>(`/api/me/profiles/${id}`, { method: "DELETE" }); await queryClient.invalidateQueries({ queryKey: ["my-profiles"] }); setMessage("프로필을 삭제했습니다."); }
    catch (cause) { setMessage(cause instanceof Error ? cause.message : "삭제하지 못했습니다."); }
    finally { setBusy(false); }
  };
  const select = (saved: SavedProfile) => {
    const { id: _id, name: _name, ...fields } = saved;
    void _id; void _name;
    update(fields);
    navigate("/results");
  };
  return <section><div className="page-head"><span className="eyebrow">마이페이지</span><h1>{user.displayName}님의 부모님 프로필</h1><p>저장한 프로필을 선택하면 바로 맞춤 혜택을 찾습니다.</p></div>
    <StateView loading={isPending} error={error} empty={data?.length === 0} />
    <div className="cards">{data?.map((saved) => <article key={saved.id} className="card"><h2>{saved.name}</h2><p>{saved.age}세 · {saved.ctpvName} {saved.sggName ?? ""}</p><div className="actions"><button className="secondary" onClick={() => select(saved)}>이 프로필로 매칭</button><button className="secondary" onClick={() => { const { id: _id, name: _name, ...fields } = saved; void _id; void _name; update(fields); setEditingId(saved.id); setName(saved.name); setMessage("정보 입력 화면에서 내용을 수정한 뒤 돌아와 저장하세요."); }}>수정 준비</button><button className="secondary" disabled={busy} onClick={() => void remove(saved.id)}>삭제</button></div></article>)}</div>
    <div className="panel section-block"><h2>{editingId === null ? "현재 입력 정보 저장" : "프로필 수정"}</h2><p>현재 입력한 나이와 지역 등 매칭 조건을 저장합니다. <Link to="/">정보 입력 화면</Link>에서 변경할 수 있습니다.</p><p className="muted">현재 입력: {profile.age}세 · {profile.ctpvName || "거주 지역 미입력"}</p><label>프로필 이름<input value={name} maxLength={100} onChange={(event) => setName(event.target.value)} placeholder="예: 어머니" /></label><div className="actions"><button className="primary" disabled={busy || !name.trim() || !profile.ctpvName} onClick={() => void save()}>{editingId === null ? "프로필 저장" : "변경 저장"}</button>{editingId !== null && <button className="secondary" onClick={() => { setEditingId(null); setName(""); }}>수정 취소</button>}</div>{message && <p role="status" className="notice">{message}</p>}</div>
  </section>;
}
