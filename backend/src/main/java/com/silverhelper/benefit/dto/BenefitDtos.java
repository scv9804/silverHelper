package com.silverhelper.benefit.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class BenefitDtos {
    private BenefitDtos() {}
    public record Code(String code, String name) {}
    public record Tag(String type, String code, String name) {}
    public record Region(String ctpvName, String sggName) {}
    public record RegionOption(String ctpvName, List<String> sggNames) {}
    public record Basis(OffsetDateTime lastCollectedAt, OffsetDateTime sourceModifiedAt, Short baseYear, boolean updatePending) {}
    public record Eligibility(Short minAge, Short maxAge, Boolean ltcRequired, List<Short> ltcGrades,
            Short incomeMaxMedianPct, Boolean requiresBasicLivelihood, Boolean requiresNearPoverty,
            Boolean requiresBasicPension, Boolean requiresDisability, Boolean requiresVeteran,
            Boolean requiresSingleHousehold, String conditionsSummary) {}
    public record Summary(Long id, Long groupId, String name, String summary, String source, String sourceUrl,
            String agencyName, Region region, String provisionType, String supportCycle, Boolean onlineApply,
            List<Tag> tags, Eligibility eligibility, Basis basis) {}
    public record Reference(String name, String value) {}
    public record OtherSource(Long id, String source, String name, String sourceUrl) {}
    public record Detail(Long id, Long groupId, String name, String summary, String source, String sourceUrl,
            String agencyName, Region region, String provisionType, String supportCycle, Boolean onlineApply,
            List<Tag> tags, Eligibility eligibility, Basis basis, String targetContent, String criteriaContent,
            String benefitContent, String applyContent, LocalDate enforceStartDate, LocalDate enforceEndDate,
            String contact, Map<String, List<Reference>> references, List<OtherSource> otherSources) {}
    public record Profile(@NotNull @Min(0) @Max(120) Integer age, @NotBlank String ctpvName, String sggName,
            @Min(1) @Max(6) Integer ltcGrade, Boolean hasNoLtcGrade,
            @Min(0) @Max(1000) Integer incomeMedianPct, Boolean basicLivelihood, Boolean nearPoverty,
            Boolean basicPension, Boolean disability, Boolean veteran, Boolean livingAlone) {}
    public record MatchResult(Summary benefit, String matchLevel, List<String> checkItems) {}
    public record MatchSummary(long eligibleCount, long checkNeededCount) {}
    public record MatchPage(List<MatchResult> content, int page, int size, long totalElements, int totalPages,
            MatchSummary summary) {}
}
