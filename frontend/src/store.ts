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
function stored(): Profile {
  try {
    const value = localStorage.getItem("silverhelper-profile");
    return value ? { ...initialProfile, ...JSON.parse(value) } : initialProfile;
  } catch {
    return initialProfile;
  }
}
type EditingProfile = { id: number; userId: number; name: string };
type State = {
  profile: Profile;
  editing: EditingProfile | null;
  update: (patch: Partial<Profile>) => void;
  startEditing: (editing: EditingProfile) => void;
  renameEditing: (name: string) => void;
  clearEditing: () => void;
};
export const useProfile = create<State>((set) => ({
  profile: stored(),
  editing: null,
  startEditing: (editing) => set({ editing }),
  renameEditing: (name) => set((state) => ({ editing: state.editing ? { ...state.editing, name } : null })),
  clearEditing: () => set({ editing: null }),
  update: (patch) =>
    set((state) => {
      const profile = { ...state.profile, ...patch };
      try {
        localStorage.setItem("silverhelper-profile", JSON.stringify(profile));
      } catch {
        /* storage unavailable */
      }
      return { profile };
    }),
}));
