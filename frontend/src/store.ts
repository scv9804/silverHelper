import { create } from "zustand";
import type { Profile } from "./api";

export const initialProfile: Profile = {
  age: 75,
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
// Older versions saved sensitive profile fields without an account boundary.
removePersistedProfile();
type EditingProfile = { id: number; userId: number; name: string };
type State = {
  profile: Profile;
  editing: EditingProfile | null;
  update: (patch: Partial<Profile>) => void;
  startEditing: (editing: EditingProfile) => void;
  renameEditing: (name: string) => void;
  clearEditing: () => void;
  reset: () => void;
};
export const useProfile = create<State>((set) => ({
  profile: { ...initialProfile },
  editing: null,
  startEditing: (editing) => set({ editing }),
  renameEditing: (name) => set((state) => ({ editing: state.editing ? { ...state.editing, name } : null })),
  clearEditing: () => set({ editing: null }),
  reset: () => {
    removePersistedProfile();
    set({ profile: { ...initialProfile }, editing: null });
  },
  update: (patch) =>
    set((state) => {
      const profile = { ...state.profile, ...patch };
      return { profile };
    }),
}));
