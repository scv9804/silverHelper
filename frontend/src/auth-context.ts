import { createContext, useContext } from "react";
import type { AuthToken, Me } from "./api";

export type AuthContextValue = { user: Me | null; ready: boolean; login: (token: AuthToken) => void; logout: () => Promise<void> };
export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("AuthProvider가 필요합니다.");
  return context;
}
