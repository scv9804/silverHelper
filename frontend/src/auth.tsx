import { useEffect, useState, type ReactNode } from "react";
import { api, refreshToken, setAccessToken, type AuthToken, type Me } from "./api";
import { AuthContext } from "./auth-context";
import { useProfile } from "./store";


export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    refreshToken().then((token) => { if (active) setUser(token.user); })
      .catch(() => {})
      .finally(() => { if (active) setReady(true); });
    return () => { active = false; };
  }, []);
  const login = (token: AuthToken) => { setAccessToken(token.accessToken); setUser(token.user); setReady(true); };
  const logout = async () => {
    try { await api<void>("/api/auth/logout", { method: "POST" }); }
    finally { setAccessToken(null); setUser(null); useProfile.getState().reset(); }
  };
  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>;
}
