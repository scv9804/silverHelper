package com.silverhelper.benefit.service;

import com.silverhelper.benefit.domain.*;
import com.silverhelper.benefit.dto.BenefitDtos.*;
import com.silverhelper.common.code.WelfareCode;
import com.silverhelper.common.exception.*;
import com.silverhelper.common.response.PageResponse;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BenefitService {
    private static final String REPRESENTATIVES = """
            select id from (select b.id, row_number() over (partition by b.group_id order by
              case when b.id = g.primary_benefit_id then 0 else 1 end,
              case b.source when 'NATIONAL' then 0 when 'LOCAL' then 1 else 2 end, b.id) rn
            from benefit b join benefit_group g on g.id=b.group_id
            where b.status='ACTIVE' and b.verified_hash is not null) ranked where rn=1
            """;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    public BenefitService(JdbcTemplate jdbc, EntityManager em) { this.jdbc = jdbc; this.em = em; }

    private List<Benefit> representatives() {
        return jdbc.queryForList(REPRESENTATIVES, Long.class).stream().map(id -> em.find(Benefit.class, id)).toList();
    }
    private BenefitEligibility eligibility(Long id) { return em.find(BenefitEligibility.class, id); }
    private boolean region(Benefit b, String ctpv, String sgg) {
        return b.ctpvName == null || ctpv != null && b.ctpvName.equals(ctpv)
                && (b.sggName == null || b.sggName.equals(sgg));
    }
    private int regionRank(Benefit b) { return b.sggName != null ? 0 : b.ctpvName != null ? 1 : 2; }
    private Comparator<Benefit> comparator(String sort) {
        Comparator<Benefit> names = Comparator.comparing(b -> b.name);
        if ("NAME".equals(sort)) return names.thenComparing(b -> b.id);
        if ("RECENT".equals(sort)) return Comparator.comparing((Benefit b) -> b.sourceModifiedAt,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(names).thenComparing(b -> b.id);
        return Comparator.comparingInt(this::regionRank).thenComparing(names).thenComparing(b -> b.id);
    }
    public PageResponse<Summary> list(Integer age, String ctpv, String sgg, Integer grade, Integer income,
            String theme, String keyword, int page, int size, String sort) {
        Set<String> themes = theme == null || theme.isBlank() ? Set.of() : Arrays.stream(theme.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<Benefit> filtered = representatives().stream().filter(b -> region(b, ctpv, sgg))
                .filter(b -> { BenefitEligibility e = eligibility(b.id);
                    return e == null || (age == null || (e.minAge == null || age >= e.minAge)
                            && (e.maxAge == null || age <= e.maxAge))
                            && (grade == null || (e.ltcGrades == null || Arrays.stream(e.ltcGrades)
                            .anyMatch(g -> g.intValue() == grade)) && !Boolean.FALSE.equals(e.ltcRequired))
                            && (income == null || e.incomeMaxMedianPct == null || income <= e.incomeMaxMedianPct); })
                .filter(b -> themes.isEmpty() || tags(b.id).stream().anyMatch(t -> t.type().equals("THEME") && themes.contains(t.code())))
                .filter(b -> keyword == null || keyword.isBlank() || b.name.contains(keyword)
                        || b.summary != null && b.summary.contains(keyword))
                .sorted(comparator(sort)).toList();
        return page(filtered.stream().map(this::summary).toList(), page, size);
    }
    public Detail detail(long id) {
        Benefit b = em.find(Benefit.class, id);
        if (b == null || !"ACTIVE".equals(b.status) || b.verifiedHash == null)
            throw new BusinessException(ErrorCode.BENEFIT_NOT_FOUND);
        Summary s = summary(b);
        Map<String, List<Reference>> refs = new LinkedHashMap<>();
        for (String type : List.of("CONTACT", "SITE", "LAW", "FORM", "APPLY")) {
            refs.put(type, em.createQuery("select r from BenefitReference r where r.benefitId=:id and r.refType=:type order by r.sortOrder, r.id", BenefitReference.class)
                    .setParameter("id", id).setParameter("type", type).getResultList().stream()
                    .map(r -> new Reference(r.name, r.value)).toList());
        }
        List<OtherSource> others = em.createQuery("select b from Benefit b where b.groupId=:groupId and b.id<>:id and b.status='ACTIVE' and b.verifiedHash is not null", Benefit.class)
                .setParameter("groupId", b.groupId).setParameter("id", id).getResultList().stream()
                .map(o -> new OtherSource(o.id, o.source, o.name, o.sourceUrl)).toList();
        return new Detail(s.id(), s.groupId(), s.name(), s.summary(), s.source(), s.sourceUrl(),
                s.agencyName(), s.region(), s.provisionType(), s.supportCycle(), s.onlineApply(), s.tags(),
                s.eligibility(), s.basis(), b.targetContent, b.criteriaContent, b.benefitContent,
                b.applyContent, b.enforceStartDate, b.enforceEndDate, b.contact, refs, others);
    }
    public MatchPage match(Profile profile, int page, int size) {
        List<MatchResult> results = representatives().stream().filter(b -> region(b, profile.ctpvName(), profile.sggName()))
                .map(b -> Map.entry(b, EligibilityMatcher.match(eligibility(b.id), profile, b.ctpvName, b.sggName)))
                .filter(x -> !x.getValue().excluded())
                .sorted(Comparator.<Map.Entry<Benefit, EligibilityMatcher.Result>>comparingInt(x -> x.getValue().checkItems().isEmpty() ? 0 : 1)
                        .thenComparingInt(x -> regionRank(x.getKey())).thenComparing(x -> x.getKey().name)
                        .thenComparing(x -> x.getKey().id))
                .map(x -> new MatchResult(summary(x.getKey()), x.getValue().level(), x.getValue().checkItems())).toList();
        long eligible = results.stream().filter(r -> r.matchLevel().equals("ELIGIBLE")).count();
        List<MatchResult> slice = slice(results, page, size);
        return new MatchPage(slice, page, size, results.size(), (results.size() + size - 1) / size,
                new MatchSummary(eligible, results.size() - eligible));
    }
    public Map<String, List<Code>> codes() {
        Map<String, List<Code>> out = new LinkedHashMap<>();
        for (WelfareCode.Type type : WelfareCode.Type.values()) out.put(type.name(), WelfareCode.of(type).stream()
                .map(c -> new Code(c.code(), c.displayName())).toList());
        return out;
    }
    public List<RegionOption> regions() {
        Map<String, SortedSet<String>> regions = new TreeMap<>();
        for (Benefit b : representatives()) if (b.ctpvName != null) {
            SortedSet<String> names = regions.computeIfAbsent(b.ctpvName, k -> new TreeSet<>());
            if (b.sggName != null) names.add(b.sggName);
        }
        return regions.entrySet().stream().map(e -> new RegionOption(e.getKey(), List.copyOf(e.getValue()))).toList();
    }
    private Summary summary(Benefit b) {
        BenefitEligibility e = eligibility(b.id);
        Eligibility dto = e == null ? new Eligibility(null,null,null,null,null,null,null,null,null,null,null,null)
                : new Eligibility(e.minAge,e.maxAge,e.ltcRequired,e.ltcGrades == null ? null : Arrays.asList(e.ltcGrades),
                e.incomeMaxMedianPct,e.requiresBasicLivelihood,e.requiresNearPoverty,e.requiresBasicPension,
                e.requiresDisability,e.requiresVeteran,e.requiresSingleHousehold,e.conditionsSummary);
        return new Summary(b.id,b.groupId,b.name,b.summary,b.source,b.sourceUrl,b.agencyName,
                b.ctpvName == null ? null : new Region(b.ctpvName,b.sggName),b.provisionType,b.supportCycle,
                b.onlineApply,tags(b.id),dto,new Basis(b.lastCollectedAt,b.sourceModifiedAt,b.baseYear,
                !Objects.equals(b.contentHash,b.verifiedHash)));
    }
    private List<Tag> tags(Long id) {
        return em.createQuery("select t from BenefitTag t where t.benefitId=:id order by t.tagType, t.code", BenefitTag.class)
                .setParameter("id", id).getResultList().stream().map(t -> new Tag(t.tagType,t.code,
                        WelfareCode.fromCode(WelfareCode.Type.valueOf(t.tagType),t.code)
                                .map(WelfareCode::displayName).orElse(t.code))).toList();
    }
    private static <T> PageResponse<T> page(List<T> all, int page, int size) {
        return new PageResponse<>(slice(all,page,size),page,size,all.size(),(all.size()+size-1)/size);
    }
    private static <T> List<T> slice(List<T> all, int page, int size) {
        long start = (long)page * size;
        return start >= all.size() ? List.of() : all.subList((int)start,(int)Math.min(all.size(),start+size));
    }
}
