import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, refreshToken, type AuthToken, type Me } from "./api";
import { AuthContext } from "./auth-context";
import { useProfile } from "./store";
import { beginClientSession, broadcastSessionChange, clearClientSession, subscribeSessionChanges } from "./session";


export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);
  const queryClient = useQueryClient();
  const generation = useRef(0);
  const clear = useCallback(() => {
    generation.current++;
    clearClientSession();
    queryClient.clear();
    setUser(null);
    setReady(true);
  }, [queryClient]);
  useEffect(() => {
    useProfile.getState().reset();
    queryClient.clear();
    let active = true;
    const current = ++generation.current;
    const unsubscribe = subscribeSessionChanges(clear);
    refreshToken().then((token) => {
      if (active && current === generation.current) setUser(token.user);
    }).catch(() => {
      if (active && current === generation.current) clear();
    }).finally(() => {
      if (active && current === generation.current) setReady(true);
    });
    return () => { active = false; unsubscribe(); };
  }, [clear, queryClient]);
  const login = (token: AuthToken) => {
    clear();
    beginClientSession(token.accessToken);
    setUser(token.user);
  };
  const logout = async () => {
    try { await api<void>("/api/auth/logout", { method: "POST" }); }
    finally { clear(); broadcastSessionChange(); }
  };
  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>;
}
