package com.silverhelper.benefit.service;

import com.silverhelper.benefit.domain.*;
import com.silverhelper.benefit.dto.BenefitDtos.*;
import com.silverhelper.common.code.WelfareCode;
import com.silverhelper.common.exception.*;
import com.silverhelper.common.response.PageResponse;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BenefitService {
    private static final String REPRESENTATIVES = """
            with ranked as (select b.id, row_number() over (partition by b.group_id order by
              case when b.id = g.primary_benefit_id then 0 else 1 end,
              case b.source when 'NATIONAL' then 0 when 'LOCAL' then 1 else 2 end, b.id) rn
            from benefit b join benefit_group g on g.id=b.group_id
            where b.status='ACTIVE' and b.verified_hash is not null)
            """;
    private static final String FILTERED = REPRESENTATIVES + """
            select b.id from ranked r join benefit b on b.id=r.id
            left join benefit_eligibility e on e.benefit_id=b.id where r.rn=1
            """;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;
    public BenefitService(NamedParameterJdbcTemplate jdbc, EntityManager em) { this.jdbc = jdbc; this.em = em; }

    private int regionRank(Benefit b) { return b.sggName != null ? 0 : b.ctpvName != null ? 1 : 2; }
    public PageResponse<Summary> list(Integer age, String ctpv, String sgg, Integer grade, Integer income,
            String theme, String keyword, int page, int size, String sort) {
        Set<String> themes = theme == null || theme.isBlank() ? Set.of() : Arrays.stream(theme.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        MapSqlParameterSource args = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder();
        if (ctpv != null) {
            where.append(" and (b.ctpv_name is null or b.ctpv_name=:ctpv)");
            args.addValue("ctpv", ctpv);
            if (sgg != null) {
                where.append(" and (b.sgg_name is null or b.sgg_name=:sgg)");
                args.addValue("sgg", sgg);
            }
        }
        if (age != null) {
            where.append(" and (e.min_age is null or e.min_age<=:age) and (e.max_age is null or e.max_age>=:age)");
            args.addValue("age", age);
        }
        if (grade != null) {
            where.append(" and e.ltc_required is distinct from false and (e.ltc_grades is null or cast(:grade as smallint)=any(e.ltc_grades))");
            args.addValue("grade", grade);
        }
        if (income != null) {
            where.append(" and (e.income_max_median_pct is null or e.income_max_median_pct>=:income)");
            args.addValue("income", income);
        }
        if (!themes.isEmpty()) {
            where.append(" and exists (select 1 from benefit_tag t where t.benefit_id=b.id and t.tag_type='THEME' and t.code in (:themes))");
            args.addValue("themes", themes);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (position(lower(:keyword) in lower(b.name))>0 or position(lower(:keyword) in lower(coalesce(b.summary,'')))>0)");
            args.addValue("keyword", keyword);
        }
        String query = FILTERED + where;
        long count = jdbc.queryForObject("select count(*) from (" + query + ") x", args, Long.class);
        String order = switch (sort) {
            case "NAME" -> "b.name, b.id";
            case "RECENT" -> "b.source_modified_at desc nulls last, b.name, b.id";
            default -> "case when b.sgg_name is not null then 0 when b.ctpv_name is not null then 1 else 2 end, b.name, b.id";
        };
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<Long> ids = jdbc.queryForList(query + " order by " + order + " limit :limit offset :offset", args, Long.class);
        Batch batch = load(ids, true);
        return new PageResponse<>(ids.stream().map(id -> summary(batch.benefits.get(id), batch)).toList(),
                page, size, count, (int) ((count + size - 1) / size));
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
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("ctpv", profile.ctpvName()).addValue("age", profile.age());
        String query = FILTERED + " and (b.ctpv_name is null or b.ctpv_name=:ctpv)" +
                " and (e.min_age is null or e.min_age<=:age) and (e.max_age is null or e.max_age>=:age)";
        if (profile.sggName() != null) {
            query += " and (b.sgg_name is null or b.sgg_name=:sgg)";
            args.addValue("sgg", profile.sggName());
        }
        List<Long> ids = jdbc.queryForList(query, args, Long.class);
        Batch candidates = load(ids, false);
        List<Map.Entry<Benefit, EligibilityMatcher.Result>> matched = ids.stream()
                .map(id -> { Benefit b = candidates.benefits.get(id);
                    return Map.entry(b, EligibilityMatcher.match(candidates.eligibilities.get(id), profile, b.ctpvName, b.sggName)); })
                .filter(x -> !x.getValue().excluded())
                .sorted(Comparator.<Map.Entry<Benefit, EligibilityMatcher.Result>>comparingInt(x -> x.getValue().checkItems().isEmpty() ? 0 : 1)
                        .thenComparingInt(x -> regionRank(x.getKey())).thenComparing(x -> x.getKey().name)
                        .thenComparing(x -> x.getKey().id)).toList();
        long eligible = matched.stream().filter(x -> x.getValue().checkItems().isEmpty()).count();
        List<Map.Entry<Benefit, EligibilityMatcher.Result>> selected = slice(matched, page, size);
        Batch pageBatch = load(selected.stream().map(x -> x.getKey().id).toList(), true);
        List<MatchResult> content = selected.stream().map(x -> new MatchResult(
                summary(x.getKey(), pageBatch), x.getValue().level(), x.getValue().checkItems())).toList();
        return new MatchPage(content, page, size, matched.size(), (matched.size() + size - 1) / size,
                new MatchSummary(eligible, matched.size() - eligible));
    }
    public Map<String, List<Code>> codes() {
        Map<String, List<Code>> out = new LinkedHashMap<>();
        for (WelfareCode.Type type : WelfareCode.Type.values()) out.put(type.name(), WelfareCode.of(type).stream()
                .map(c -> new Code(c.code(), c.displayName())).toList());
        return out;
    }
    public List<RegionOption> regions() {
        Map<String, SortedSet<String>> regions = new TreeMap<>();
        jdbc.getJdbcTemplate().query("select distinct ctpv_name,sgg_name from benefit where status='ACTIVE' and verified_hash is not null and ctpv_name is not null", rs -> {
            SortedSet<String> names = regions.computeIfAbsent(rs.getString(1), k -> new TreeSet<>());
            if (rs.getString(2) != null) names.add(rs.getString(2));
        });
        return regions.entrySet().stream().map(e -> new RegionOption(e.getKey(), List.copyOf(e.getValue()))).toList();
    }
    private Summary summary(Benefit b) { return summary(b, load(List.of(b.id), true)); }
    private Summary summary(Benefit b, Batch batch) {
        BenefitEligibility e = batch.eligibilities.get(b.id);
        Eligibility dto = e == null ? new Eligibility(null,null,null,null,null,null,null,null,null,null,null,null)
                : new Eligibility(e.minAge,e.maxAge,e.ltcRequired,e.ltcGrades == null ? null : Arrays.asList(e.ltcGrades),
                e.incomeMaxMedianPct,e.requiresBasicLivelihood,e.requiresNearPoverty,e.requiresBasicPension,
                e.requiresDisability,e.requiresVeteran,e.requiresSingleHousehold,e.conditionsSummary);
        return new Summary(b.id,b.groupId,b.name,b.summary,b.source,b.sourceUrl,b.agencyName,
                b.ctpvName == null ? null : new Region(b.ctpvName,b.sggName),b.provisionType,b.supportCycle,
                b.onlineApply,batch.tags.getOrDefault(b.id,List.of()),dto,new Basis(b.lastCollectedAt,b.sourceModifiedAt,b.baseYear,
                !Objects.equals(b.contentHash,b.verifiedHash)));
    }
    private Batch load(List<Long> ids, boolean withTags) {
        if (ids.isEmpty()) return new Batch(Map.of(),Map.of(),Map.of());
        Map<Long,Benefit> benefits = em.createQuery("select b from Benefit b where b.id in :ids",Benefit.class)
                .setParameter("ids",ids).getResultList().stream().collect(Collectors.toMap(b -> b.id,b -> b));
        Map<Long,BenefitEligibility> eligibilities = em.createQuery("select e from BenefitEligibility e where e.benefitId in :ids",BenefitEligibility.class)
                .setParameter("ids",ids).getResultList().stream().collect(Collectors.toMap(e -> e.benefitId,e -> e));
        Map<Long,List<Tag>> tags = new HashMap<>();
        if (withTags) em.createQuery("select t from BenefitTag t where t.benefitId in :ids order by t.tagType,t.code",BenefitTag.class)
                .setParameter("ids",ids).getResultList().forEach(t -> tags.computeIfAbsent(t.benefitId,k -> new ArrayList<>())
                .add(new Tag(t.tagType,t.code,WelfareCode.fromCode(WelfareCode.Type.valueOf(t.tagType),t.code)
                        .map(WelfareCode::displayName).orElse(t.code))));
        return new Batch(benefits,eligibilities,tags);
    }
    private record Batch(Map<Long,Benefit> benefits,Map<Long,BenefitEligibility> eligibilities,Map<Long,List<Tag>> tags) {}
    private static <T> List<T> slice(List<T> all, int page, int size) {
        long start = (long)page * size;
        return start >= all.size() ? List.of() : all.subList((int)start,(int)Math.min(all.size(),start+size));
    }
}
