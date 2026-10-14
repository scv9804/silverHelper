import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, type Me, type NotificationSettings } from "../api";
import { useAuth } from "../auth-context";
import { StateView } from "../components/StateView";
import { clearForAccount, settleForAccount, valueForAccount, type AccountValue } from "../account-draft";

export function NotificationPage() {
  const { user, ready } = useAuth();
  if (!ready) return <p>로그인 상태를 확인하고 있습니다.</p>;
  if (!user) return <div className="panel"><h1>로그인이 필요합니다</h1><Link to="/login">로그인하기</Link></div>;
  return <NotificationEditor key={user.id} user={user} />;
}

function NotificationEditor({ user }: { user: Me }) {
  const client = useQueryClient();
  const [draft, setDraft] = useState<AccountValue<NotificationSettings> | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [message, setMessage] = useState<AccountValue<string> | null>(null);
  const query = useQuery({ queryKey: ["notification-settings", user.id], queryFn: () => api<NotificationSettings>("/api/me/notification-settings") });
  const value = valueForAccount(draft, user.id) ?? query.data;
  const patch = (next: Partial<NotificationSettings>) => setDraft({ accountId: user.id, value: { ...value!, ...next } });
  const save = async () => {
    if (!value || value.deadlineDays < 1 || value.deadlineDays > 30) return;
    const accountId = user.id;
    setBusy(accountId); setMessage(null);
    try {
      const saved = await api<NotificationSettings>("/api/me/notification-settings", { method: "PUT", body: JSON.stringify(value) });
      client.setQueryData(["notification-settings", accountId], saved);
      setDraft((current) => clearForAccount(current, accountId));
      setMessage((current) => settleForAccount(current, accountId, "알림 설정을 저장했습니다."));
    } catch (error) { setMessage((current) => settleForAccount(current, accountId, error instanceof Error ? error.message : "설정을 저장하지 못했습니다.")); }
    finally { setBusy((current) => current === accountId ? null : current); }
  };
  return <section className="narrow"><div className="page-head"><h1>알림 설정</h1><p>관심 혜택의 새 소식과 마감 알림을 관리합니다.</p></div><StateView loading={query.isPending} error={query.error} />
    {value && <div className="panel stack"><p className="notice">{!user.email ? "계정에 이메일 주소가 없어 메일을 받을 수 없습니다." : "이메일 인증 상태는 이 화면에서 확인할 수 없습니다. 메일이 오지 않으면 계정 이메일 인증을 확인해 주세요."}</p>
      <label className="toggle"><input type="checkbox" checked={value.emailEnabled} disabled={!user.email} onChange={(event) => patch({ emailEnabled: event.target.checked })} /> 이메일 알림 켜기</label>
      <label className="toggle"><input type="checkbox" checked={value.newBenefitEnabled} onChange={(event) => patch({ newBenefitEnabled: event.target.checked })} /> 새 혜택 알림</label>
      <label className="toggle"><input type="checkbox" checked={value.deadlineEnabled} onChange={(event) => patch({ deadlineEnabled: event.target.checked })} /> 마감 임박 알림</label>
      <label>마감 며칠 전 알림 (1~30일)<input type="number" min="1" max="30" value={value.deadlineDays} onChange={(event) => patch({ deadlineDays: Number(event.target.value) })} /></label>
      <button className="primary" disabled={busy === user.id || value.deadlineDays < 1 || value.deadlineDays > 30} onClick={() => void save()}>설정 저장</button>{valueForAccount(message, user.id) && <p role="status" className="notice">{valueForAccount(message, user.id)}</p>}
    </div>}<p><Link to="/me">← 마이페이지</Link></p></section>;
}
