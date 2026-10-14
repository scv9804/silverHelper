import type { Eligibility } from "./api";

export const emptyEligibility: Eligibility = {
  minAge: null,
  maxAge: null,
  ltcRequired: null,
  ltcGrades: null,
  incomeMaxMedianPct: null,
  requiresBasicLivelihood: null,
  requiresNearPoverty: null,
  requiresBasicPension: null,
  requiresDisability: null,
  requiresVeteran: null,
  requiresSingleHousehold: null,
  conditionsSummary: null,
};
