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
type State = { profile: Profile; update: (patch: Partial<Profile>) => void };
export const useProfile = create<State>((set) => ({
  profile: stored(),
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
