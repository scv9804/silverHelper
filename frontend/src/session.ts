import { SESSION_EXPIRED_EVENT, setAccessToken } from "./api.ts";
import { useProfile } from "./store.ts";

export const SESSION_CHANGE_KEY = "silverhelper-session-change";

export function clearClientSession() {
  setAccessToken(null);
  useProfile.getState().reset();
}

export function beginClientSession(accessToken: string) {
  setAccessToken(accessToken);
  useProfile.getState().clearEditing();
  useProfile.getState().setGuest(false);
  broadcastSessionChange("login");
}

export function broadcastSessionChange(action: "login" | "logout" = "logout") {
  try {
    localStorage.setItem(SESSION_CHANGE_KEY, `${action}:${Date.now()}:${Math.random()}`);
  } catch {
    /* Another tab cannot be notified when storage is unavailable. */
  }
}

export function subscribeSessionChanges(onChange: (action: "login" | "logout") => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key === SESSION_CHANGE_KEY && event.newValue) onChange(event.newValue.startsWith("login:") ? "login" : "logout");
  };
  window.addEventListener("storage", onStorage);
  const onExpired = () => onChange("logout");
  window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  };
}
