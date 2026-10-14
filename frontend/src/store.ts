import { create } from "zustand";
import type { Profile } from "./api";

export const initialProfile: Profile = {
  age: 0,
  ctpvName: "",
  sggName: null,
  ltcGrade: null,
  hasNoLtcGrade: null,
  incomeMedianPct: null,
  basicLivelihood: null,
  nearPoverty: null,
  basicPension: null,
  disability: null,
  veteran: null,
  livingAlone: null,
};
function removePersistedProfile() {
  try {
    localStorage.removeItem("silverhelper-profile");
  } catch {
    /* storage unavailable */
  }
}
const GUEST_KEY = "silverhelper-guest-profile";
const LAST_PROFILE_KEY = "silverhelper-last-profile";
function readGuest(): Profile | null {
  try {
    const raw = sessionStorage.getItem(GUEST_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as Profile;
    return typeof value.age === "number" && typeof value.ctpvName === "string" ? value : null;
  } catch { return null; }
}
export function rememberProfile(userId: number, profileId: number) {
  try { localStorage.setItem(LAST_PROFILE_KEY, JSON.stringify({ userId, profileId })); } catch { /* storage unavailable */ }
}
export function lastProfileId(userId: number): number | null {
  try { const value = JSON.parse(localStorage.getItem(LAST_PROFILE_KEY) || "null"); return value?.userId === userId ? value.profileId : null; } catch { return null; }
}
export function clearLastProfile() {
  try { localStorage.removeItem(LAST_PROFILE_KEY); } catch { /* storage unavailable */ }
}
export function clearGuestProfile() {
  try { sessionStorage.removeItem(GUEST_KEY); } catch { /* storage unavailable */ }
}
// Older versions saved sensitive profile fields without an account boundary.
removePersistedProfile();
type EditingProfile = { id: number; userId: number; name: string };
type State = {
  profile: Profile;
  guest: boolean;
  hasInput: boolean;
  editing: EditingProfile | null;
  update: (patch: Partial<Profile>) => void;
  startEditing: (editing: EditingProfile) => void;
  renameEditing: (name: string) => void;
  clearEditing: () => void;
  reset: () => void;
  setGuest: (guest: boolean) => void;
  loadProfile: (profile: Profile) => void;
};
export const useProfile = create<State>((set) => ({
  profile: readGuest() ?? { ...initialProfile },
  guest: true,
  hasInput: !!readGuest(),
  editing: null,
  startEditing: (editing) => set({ editing }),
  renameEditing: (name) => set((state) => ({ editing: state.editing ? { ...state.editing, name } : null })),
  clearEditing: () => set({ editing: null }),
  reset: () => {
    removePersistedProfile();
    clearGuestProfile();
    clearLastProfile();
    set({ profile: { ...initialProfile }, editing: null, hasInput: false, guest: true });
  },
  setGuest: (guest) => set({ guest }),
  loadProfile: (profile) => set({ profile, hasInput: true }),
  update: (patch) =>
    set((state) => {
      const profile = { ...state.profile, ...patch };
      if (state.guest) {
        try { sessionStorage.setItem(GUEST_KEY, JSON.stringify(profile)); } catch { /* storage unavailable */ }
      }
      return { profile, hasInput: true };
    }),
}));
