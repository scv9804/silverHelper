import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, refreshToken, type AuthToken, type Me } from "./api";
import { AuthContext } from "./auth-context";
import { useProfile } from "./store";
import { beginClientSession, broadcastSessionChange, clearClientSession, subscribeSessionChanges } from "./session";
import { restoreSelectedProfile } from "./profile-restore";


export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);
  const queryClient = useQueryClient();
  const generation = useRef(0);
  const activeAccount = useRef<number | null>(null);
  const clear = useCallback(() => {
    generation.current++;
    activeAccount.current = null;
    clearClientSession();
    queryClient.clear();
    setUser(null);
    setReady(true);
  }, [queryClient]);
  const restoreProfile = useCallback(async (account: Me) => {
    const requestGeneration = generation.current;
    await restoreSelectedProfile(account, () => generation.current === requestGeneration && activeAccount.current === account.id);
  }, []);
  useEffect(() => {
    queryClient.clear();
    let active = true;
    const current = ++generation.current;
    const unsubscribe = subscribeSessionChanges((action) => {
      if (action === "logout") { clear(); return; }
      clear();
      const requestGeneration = generation.current;
      refreshToken().catch(() => refreshToken()).then((token) => {
        if (requestGeneration !== generation.current) return;
        activeAccount.current = token.user.id;
        setUser(token.user);
        void restoreProfile(token.user);
      }).catch(() => { if (requestGeneration === generation.current) clear(); });
    });
    refreshToken().then((token) => {
      if (active && current === generation.current) { activeAccount.current = token.user.id; setUser(token.user); void restoreProfile(token.user); }
    }).catch(() => {
      if (active && current === generation.current) setReady(true);
    }).finally(() => {
      if (active && current === generation.current) setReady(true);
    });
    return () => { active = false; unsubscribe(); };
  }, [clear, queryClient, restoreProfile]);
  const login = (token: AuthToken) => {
    generation.current++;
    queryClient.clear();
    if (user && user.id !== token.user.id) useProfile.getState().reset();
    beginClientSession(token.accessToken);
    activeAccount.current = token.user.id;
    setUser(token.user);
    setReady(true);
    void restoreProfile(token.user);
  };
  const logout = async () => {
    try { await api<void>("/api/auth/logout", { method: "POST" }); }
    finally { clear(); broadcastSessionChange(); }
  };
  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>;
}
