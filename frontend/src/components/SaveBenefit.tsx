import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth-context";

export function SaveBenefit({ id }: { id: number }) {
  const { user } = useAuth();
  const client = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const saved = useQuery({ queryKey: ["saved-benefits", user?.id], queryFn: () => api<number[]>("/api/me/saved-benefits"), enabled: !!user });
  if (!user) return <Link className="secondary" to="/login" title="로그인 후 관심 혜택을 저장할 수 있습니다">로그인하고 저장</Link>;
  const isSaved = saved.data?.includes(id) ?? false;
  const toggle = async () => {
    setBusy(true); setMessage("");
    try {
      if (isSaved) await api<void>(`/api/me/saved-benefits/${id}`, { method: "DELETE" });
      else await api<void>("/api/me/saved-benefits", { method: "POST", body: JSON.stringify({ benefitId: id }) });
      await client.invalidateQueries({ queryKey: ["saved-benefits", user.id] });
    } catch (error) { setMessage(error instanceof Error ? error.message : "저장 상태를 변경하지 못했습니다."); }
    finally { setBusy(false); }
  };
  return <div className="save-benefit"><button className="secondary" type="button" disabled={busy || saved.isPending || !!saved.error} onClick={() => void toggle()}>{isSaved ? "관심 혜택 해제" : "관심 혜택 저장"}</button>{saved.isPending && <span>저장 상태 확인 중</span>}{saved.error && <span role="alert">저장 상태를 불러오지 못했습니다: {saved.error.message}</span>}{message && <span role="alert">{message}</span>}</div>;
}
