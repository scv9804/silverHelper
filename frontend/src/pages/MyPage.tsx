import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, type Benefit, type SavedProfile } from "../api";
import { useAuth } from "../auth-context";
import { useProfile, rememberProfile } from "../store";
import { StateView } from "../components/StateView";

export function MyPage() {
  const { user, ready } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { profile, update, editing, startEditing, renameEditing, clearEditing, hasInput } = useProfile();
  const [newName, setNewName] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const { data, isPending, error } = useQuery({ queryKey: ["my-profiles", user?.id], queryFn: () => api<SavedProfile[]>("/api/me/profiles"), enabled: !!user });
  const savedBenefits = useQuery({ queryKey: ["saved-benefits", user?.id], queryFn: () => api<number[]>("/api/me/saved-benefits"), enabled: !!user });
  const savedDetails = useQuery({ queryKey: ["saved-benefit-details", user?.id, savedBenefits.data], queryFn: () => Promise.all((savedBenefits.data ?? []).map(async (id) => { try { return await api<Benefit>(`/api/benefits/${id}`); } catch { return null; } })), enabled: !!user && !!savedBenefits.data?.length });
  if (!ready) return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!user) return <div className="panel"><h1>로그인이 필요합니다</h1><Link to="/login">로그인하기</Link></div>;
  const editingId = editing?.userId === user.id ? editing.id : null;
  const name = editingId === null ? newName : editing?.name ?? "";
  const save = async () => {
    if (!name.trim() || !profile.ctpvName) { setMessage("이름과 거주 지역을 입력해 주세요."); return; }
    setBusy(true); setMessage("");
    try {
      const saved = await api<SavedProfile>(editingId === null ? "/api/me/profiles" : `/api/me/profiles/${editingId}`, { method: editingId === null ? "POST" : "PUT", body: JSON.stringify({ ...profile, name: name.trim() }) });
      rememberProfile(user.id, saved.id);
      await queryClient.invalidateQueries({ queryKey: ["my-profiles"] });
      setMessage("프로필을 저장했습니다."); clearEditing(); setNewName("");
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "저장하지 못했습니다."); }
    finally { setBusy(false); }
  };
  const remove = async (id: number) => {
    if (!window.confirm("이 프로필을 삭제할까요?")) return;
    setBusy(true); setMessage("");
    try { await api<void>(`/api/me/profiles/${id}`, { method: "DELETE" }); if (editingId === id) clearEditing(); await queryClient.invalidateQueries({ queryKey: ["my-profiles"] }); setMessage("프로필을 삭제했습니다."); }
    catch (cause) { setMessage(cause instanceof Error ? cause.message : "삭제하지 못했습니다."); }
    finally { setBusy(false); }
  };
  const select = (saved: SavedProfile) => {
    const { id: _id, name: _name, ...fields } = saved;
    void _id; void _name;
    update(fields);
    rememberProfile(user.id, saved.id);
    clearEditing();
    navigate("/results");
  };
  return <section><div className="page-head"><span className="eyebrow">마이페이지</span><h1>{user.displayName}님의 부모님 프로필</h1><p>저장한 프로필을 선택하면 바로 맞춤 혜택을 찾습니다.</p></div>
    <StateView loading={isPending} error={error} empty={data?.length === 0} emptyMessage="저장한 프로필이 없습니다. 현재 입력 정보를 아래에서 저장할 수 있습니다." />
    <div className="cards">{data?.map((saved) => <article key={saved.id} className="card"><h2>{saved.name}</h2><p>{saved.age}세 · {saved.ctpvName} {saved.sggName ?? ""}</p><div className="actions"><button className="secondary" onClick={() => select(saved)}>이 프로필로 매칭</button><button className="secondary" onClick={() => { const { id: _id, name: _name, ...fields } = saved; void _id; void _name; update(fields); startEditing({ id: saved.id, userId: user.id, name: saved.name }); navigate("/"); }}>정보 수정</button><button className="secondary" disabled={busy} onClick={() => void remove(saved.id)}>삭제</button></div></article>)}</div>
    <div className="panel section-block"><h2>{editingId === null ? "현재 입력 정보 저장" : "프로필 수정"}</h2><p>{hasInput ? "현재 입력 정보를 계정에 저장해 다음에도 사용할 수 있습니다." : "먼저 부모님 정보를 입력해 주세요."} <Link to="/">정보 입력 화면</Link></p><p className="muted">현재 입력: {profile.age ? `${profile.age}세` : "나이 미입력"} · {profile.ctpvName || "거주 지역 미입력"}</p><label>프로필 이름<input value={name} maxLength={100} onChange={(event) => editingId === null ? setNewName(event.target.value) : renameEditing(event.target.value)} placeholder="예: 어머니" /></label><div className="actions"><button className="primary" disabled={busy || !name.trim() || !profile.age || !profile.ctpvName} onClick={() => void save()}>{editingId === null ? "프로필 저장" : "변경 저장"}</button>{editingId !== null && <button className="secondary" onClick={() => clearEditing()}>수정 취소</button>}</div>{(!name.trim() || !profile.age || !profile.ctpvName) && <p className="muted">저장하려면 {[( !name.trim() && "프로필 이름"), (!profile.age && "나이"), (!profile.ctpvName && "거주 시도")].filter(Boolean).join(", ")}을(를) 입력해 주세요.</p>}{message && <p role="status" className="notice">{message}</p>}</div>
    <div className="panel section-block"><h2>관심 혜택</h2><StateView loading={savedBenefits.isPending || savedDetails.isPending && !!savedBenefits.data?.length} error={savedBenefits.error || savedDetails.error} empty={savedBenefits.data?.length === 0} emptyMessage="저장한 관심 혜택이 없습니다." /><ul className="saved-list">{savedDetails.data?.map((benefit, index) => <li key={savedBenefits.data?.[index]}>{benefit ? <Link to={`/benefits/${benefit.id}`}>{benefit.name}</Link> : `혜택 #${savedBenefits.data?.[index]} (현재 조회할 수 없음)`}</li>)}</ul></div>
    <p><Link className="secondary" to="/me/notifications">알림 설정</Link></p>
  </section>;
}
