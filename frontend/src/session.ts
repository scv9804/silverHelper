import { SESSION_EXPIRED_EVENT, setAccessToken } from "./api.ts";
import { useProfile } from "./store.ts";

export const SESSION_CHANGE_KEY = "silverhelper-session-change";

export function clearClientSession() {
  setAccessToken(null);
  useProfile.getState().reset();
}

export function beginClientSession(accessToken: string) {
  clearClientSession();
  setAccessToken(accessToken);
  broadcastSessionChange();
}

export function broadcastSessionChange() {
  try {
    localStorage.setItem(SESSION_CHANGE_KEY, `${Date.now()}:${Math.random()}`);
  } catch {
    /* Another tab cannot be notified when storage is unavailable. */
  }
}

export function subscribeSessionChanges(onChange: () => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key === SESSION_CHANGE_KEY && event.newValue) onChange();
  };
  window.addEventListener("storage", onStorage);
  window.addEventListener(SESSION_EXPIRED_EVENT, onChange);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(SESSION_EXPIRED_EVENT, onChange);
  };
}
