import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, refreshToken, type AuthToken, type Me, type SavedProfile } from "./api";
import { AuthContext } from "./auth-context";
import { useProfile } from "./store";
import { beginClientSession, broadcastSessionChange, clearClientSession, subscribeSessionChanges } from "./session";
import { lastProfileId } from "./store";


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
  const restoreProfile = useCallback(async (account: Me) => {
    const state = useProfile.getState();
    state.setGuest(false);
    if (state.hasInput) return;
    const id = lastProfileId(account.id);
    if (id === null) return;
    try {
      const profiles = await api<SavedProfile[]>("/api/me/profiles");
      const saved = profiles.find((item) => item.id === id);
      if (saved && !useProfile.getState().hasInput) {
        const { id: _id, name: _name, ...fields } = saved;
        void _id; void _name;
        useProfile.getState().loadProfile(fields);
      }
    } catch { /* Profile list remains available on My Page. */ }
  }, []);
  useEffect(() => {
    queryClient.clear();
    let active = true;
    const current = ++generation.current;
    const unsubscribe = subscribeSessionChanges((action) => {
      if (action === "logout") { clear(); return; }
      clear();
      refreshToken().catch(() => refreshToken()).then((token) => {
        setUser(token.user);
        void restoreProfile(token.user);
      }).catch(() => clear());
    });
    refreshToken().then((token) => {
      if (active && current === generation.current) { setUser(token.user); void restoreProfile(token.user); }
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
