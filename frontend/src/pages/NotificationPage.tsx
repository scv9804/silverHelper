import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, type NotificationSettings } from "../api";
import { useAuth } from "../auth-context";
import { StateView } from "../components/StateView";

export function NotificationPage() {
  const { user, ready } = useAuth();
  const client = useQueryClient();
  const [draft, setDraft] = useState<NotificationSettings | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const query = useQuery({ queryKey: ["notification-settings", user?.id], queryFn: () => api<NotificationSettings>("/api/me/notification-settings"), enabled: !!user });
  if (!ready) return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!user) return <div className="panel"><h1>로그인이 필요합니다</h1><Link to="/login">로그인하기</Link></div>;
  const value = draft ?? query.data;
  const patch = (next: Partial<NotificationSettings>) => setDraft({ ...value!, ...next });
  const save = async () => {
    if (!value || value.deadlineDays < 1 || value.deadlineDays > 30) return;
    setBusy(true); setMessage("");
    try {
      const saved = await api<NotificationSettings>("/api/me/notification-settings", { method: "PUT", body: JSON.stringify(value) });
      client.setQueryData(["notification-settings", user.id], saved);
      setDraft(null); setMessage("알림 설정을 저장했습니다.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "설정을 저장하지 못했습니다."); }
    finally { setBusy(false); }
  };
  return <section className="narrow"><div className="page-head"><h1>알림 설정</h1><p>관심 혜택의 새 소식과 마감 알림을 관리합니다.</p></div><StateView loading={query.isPending} error={query.error} />
    {value && <div className="panel stack"><p className="notice">{!user.email ? "계정에 이메일 주소가 없어 메일을 받을 수 없습니다." : "이메일 인증 상태는 이 화면에서 확인할 수 없습니다. 메일이 오지 않으면 계정 이메일 인증을 확인해 주세요."}</p>
      <label className="toggle"><input type="checkbox" checked={value.emailEnabled} disabled={!user.email} onChange={(event) => patch({ emailEnabled: event.target.checked })} /> 이메일 알림 켜기</label>
      <label className="toggle"><input type="checkbox" checked={value.newBenefitEnabled} onChange={(event) => patch({ newBenefitEnabled: event.target.checked })} /> 새 혜택 알림</label>
      <label className="toggle"><input type="checkbox" checked={value.deadlineEnabled} onChange={(event) => patch({ deadlineEnabled: event.target.checked })} /> 마감 임박 알림</label>
      <label>마감 며칠 전 알림 (1~30일)<input type="number" min="1" max="30" value={value.deadlineDays} onChange={(event) => patch({ deadlineDays: Number(event.target.value) })} /></label>
      <button className="primary" disabled={busy || value.deadlineDays < 1 || value.deadlineDays > 30} onClick={() => void save()}>설정 저장</button>{message && <p role="status" className="notice">{message}</p>}
    </div>}<p><Link to="/me">← 마이페이지</Link></p></section>;
}
