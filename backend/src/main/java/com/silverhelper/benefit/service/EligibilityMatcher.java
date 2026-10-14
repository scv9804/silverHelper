package com.silverhelper.benefit.service;

import com.silverhelper.benefit.domain.BenefitEligibility;
import com.silverhelper.benefit.dto.BenefitDtos.Profile;
import com.silverhelper.pipeline.service.SourceMapper;
import com.silverhelper.pipeline.service.RegionNames;
import java.util.*;

/** 순수 자격 판정. null 조건은 제한이 없고, null 프로필 값은 확인이 필요하다. */
public final class EligibilityMatcher {
    private EligibilityMatcher() {}
    public record Result(boolean excluded, List<String> checkItems) {
        public String level() { return checkItems.isEmpty() ? "ELIGIBLE" : "CHECK_NEEDED"; }
    }
    public static Result match(BenefitEligibility e, Profile p, String ctpv, String sgg) {
        boolean unknownRegion = SourceMapper.REGION_UNKNOWN.equals(ctpv);
        if (ctpv != null && !unknownRegion && !RegionNames.normalize(ctpv).equals(RegionNames.normalize(p.ctpvName()))
                || sgg != null && p.sggName() != null && !sgg.equals(p.sggName()))
            return new Result(true, List.of());
        List<String> checks = new ArrayList<>();
        if (unknownRegion) checks.add("사업 지역을 확인하세요.");
        if (sgg != null && p.sggName() == null) checks.add("거주 시군구를 확인하세요.");
        if (e == null) return new Result(false, List.copyOf(checks));
        if (e.minAge != null && p.age() < e.minAge || e.maxAge != null && p.age() > e.maxAge)
            return new Result(true, List.of());
        boolean ltcConstraint = Boolean.TRUE.equals(e.ltcRequired) || e.ltcGrades != null && e.ltcGrades.length > 0;
        if (ltcConstraint) {
            if (p.ltcGrade() != null) {
                if (e.ltcGrades != null && Arrays.stream(e.ltcGrades).noneMatch(g -> g.intValue() == p.ltcGrade()))
                    return new Result(true, List.of());
            } else if (Boolean.TRUE.equals(p.hasNoLtcGrade())) return new Result(true, List.of());
            else checks.add("장기요양 등급을 확인하세요.");
        } else if (Boolean.FALSE.equals(e.ltcRequired) && p.ltcGrade() != null) return new Result(true, List.of());
        if (e.incomeMaxMedianPct != null) {
            if (p.incomeMedianPct() == null) checks.add("기준 중위소득 비율을 확인하세요.");
            else if (p.incomeMedianPct() > e.incomeMaxMedianPct) return new Result(true, List.of());
        }
        if (!check(e.requiresBasicLivelihood, p.basicLivelihood(), "기초생활수급 여부", checks)
                || !check(e.requiresNearPoverty, p.nearPoverty(), "차상위계층 여부", checks)
                || !check(e.requiresBasicPension, p.basicPension(), "기초연금 수급 여부", checks)
                || !check(e.requiresDisability, p.disability(), "장애 등록 여부", checks)
                || !check(e.requiresVeteran, p.veteran(), "국가보훈대상 여부", checks)
                || !check(e.requiresSingleHousehold, p.livingAlone(), "1인 가구 여부", checks))
            return new Result(true, List.of());
        return new Result(false, List.copyOf(checks));
    }
    private static boolean check(Boolean required, Boolean actual, String label, List<String> checks) {
        if (required == null) return true;
        if (actual == null) { checks.add(label + "를 확인하세요."); return true; }
        return required.equals(actual);
    }
}
