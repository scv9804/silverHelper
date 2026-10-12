package com.silverhelper.pipeline.repository;

import com.silverhelper.pipeline.domain.DetailFields;
import com.silverhelper.pipeline.domain.ListFields;
import com.silverhelper.pipeline.domain.RawKind;
import com.silverhelper.pipeline.domain.Reference;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.domain.TagCode;
import java.sql.Array;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * benefit, benefit_group, benefit_tag, benefit_reference 쓰기.
 * 이 테이블의 JPA 엔티티는 benefit 패키지(읽기용)에 있으므로 파이프라인은 JDBC로만 쓴다 (ADR 0002 결과).
 */
@Repository
public class BenefitStore {

	/** 시니어 우선순위: 생애주기 노년 또는 이름에 노인·어르신·장기요양·치매·노년 (public-api.md §8) */
	static final String SENIOR_PRIORITY = """
			case when exists (select 1 from benefit_tag t where t.benefit_id = b.id and t.tag_type = 'LIFE' and t.code = '006')
			       or b.name ~ '(노인|어르신|장기요양|치매|노년)' then 1 else 0 end""";

	public record Existing(long id, long groupId, String sourceId, String status, String contentHash, String listHash) {
	}

	public record Target(long id, String sourceId) {
	}

	/** 그룹 병합 판단용 최소 정보 */
	public record GroupMember(long id, Source source, String name, String ctpvName, String sggName,
			String agencyName, long groupId) {
	}

	public record Summary(long id, Source source, String name, String sourceUrl, String contentHash,
			String verifiedHash, String status, long groupId) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public BenefitStore(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Map<String, Existing> loadBySource(Source source) {
		Map<String, Existing> result = new HashMap<>();
		jdbc.query("""
				select b.id, b.group_id, b.source_id, b.status, b.content_hash,
				       (select r.content_hash from benefit_raw r
				         where r.benefit_id = b.id and r.kind = 'LIST'
				         order by r.last_seen_at desc, r.id desc limit 1) as list_hash
				  from benefit b
				 where b.source = :source
				""", Map.of("source", source.name()), rs -> {
			Existing e = new Existing(rs.getLong("id"), rs.getLong("group_id"), rs.getString("source_id"),
					rs.getString("status"), rs.getString("content_hash"), rs.getString("list_hash"));
			result.put(e.sourceId(), e);
		});
		return result;
	}

	public Optional<Summary> findSummary(long id) {
		return jdbc.query("""
				select id, source, name, source_url, content_hash, verified_hash, status, group_id
				  from benefit where id = :id
				""", Map.of("id", id), (rs, i) -> new Summary(rs.getLong("id"), Source.valueOf(rs.getString("source")),
				rs.getString("name"), rs.getString("source_url"), rs.getString("content_hash"),
				rs.getString("verified_hash"), rs.getString("status"), rs.getLong("group_id"))).stream().findFirst();
	}

	/** 새 혜택은 자기만의 그룹을 하나 만들고 그 그룹의 대표가 된다 (ADR 0002 §3) */
	public long insert(Source source, String sourceId, ListFields f, String contentHash, OffsetDateTime now) {
		Long groupId = jdbc.queryForObject(
				"insert into benefit_group (created_at, updated_at) values (:now, :now) returning id",
				Map.of("now", now), Long.class);
		MapSqlParameterSource params = listParams(f)
				.addValue("source", source.name())
				.addValue("sourceId", sourceId)
				.addValue("groupId", groupId)
				.addValue("contentHash", contentHash)
				.addValue("now", now);
		Long id = jdbc.queryForObject("""
				insert into benefit (source, source_id, group_id, name, summary, source_url, agency_name, department_name,
				                     ctpv_name, sgg_name, support_cycle, provision_type, apply_method_name, online_apply,
				                     contact, source_registered_on, source_modified_at, content_hash, status,
				                     first_collected_at, last_collected_at, created_at, updated_at)
				values (:source, :sourceId, :groupId, :name, :summary, :sourceUrl, :agencyName, :departmentName,
				        :ctpvName, :sggName, :supportCycle, :provisionType, :applyMethodName, :onlineApply,
				        :contact, :sourceRegisteredOn, :sourceModifiedAt, :contentHash, 'ACTIVE',
				        :now, :now, :now, :now)
				returning id
				""", params, Long.class);
		jdbc.update("update benefit_group set primary_benefit_id = :id where id = :groupId",
				Map.of("id", id, "groupId", groupId));
		replaceTags(id, f.tags());
		return id;
	}

	/** 목록 값으로 덮어쓴다. 사라졌다가 다시 나타난 혜택은 ACTIVE로 돌아온다. 시행 종료일이 지났으면 ENDED */
	public void updateList(long id, ListFields f, OffsetDateTime now, LocalDate today) {
		MapSqlParameterSource params = listParams(f)
				.addValue("id", id)
				.addValue("now", now)
				.addValue("today", today);
		jdbc.update("""
				update benefit
				   set name = :name, summary = :summary, source_url = :sourceUrl, agency_name = :agencyName,
				       department_name = :departmentName, ctpv_name = :ctpvName, sgg_name = :sggName,
				       support_cycle = :supportCycle, provision_type = :provisionType,
				       apply_method_name = :applyMethodName, online_apply = :onlineApply,
				       contact = coalesce(:contact, contact), source_registered_on = :sourceRegisteredOn,
				       source_modified_at = :sourceModifiedAt,
				       status = case when enforce_end_date is not null and enforce_end_date < :today then 'ENDED' else 'ACTIVE' end,
				       last_collected_at = :now, updated_at = :now
				 where id = :id
				""", params);
		replaceTags(id, f.tags());
	}

	public void applyDetail(long id, DetailFields f, OffsetDateTime now, LocalDate today) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("id", id)
				.addValue("baseYear", f.baseYear())
				.addValue("enforceStart", f.enforceStartDate())
				.addValue("enforceEnd", f.enforceEndDate())
				.addValue("contact", f.contact())
				.addValue("targetContent", f.targetContent())
				.addValue("criteriaContent", f.criteriaContent())
				.addValue("benefitContent", f.benefitContent())
				.addValue("applyContent", f.applyContent())
				.addValue("now", now)
				.addValue("today", today);
		jdbc.update("""
				update benefit
				   set base_year = :baseYear, enforce_start_date = cast(:enforceStart as date),
				       enforce_end_date = cast(:enforceEnd as date), contact = coalesce(:contact, contact),
				       target_content = :targetContent, criteria_content = :criteriaContent,
				       benefit_content = :benefitContent, apply_content = :applyContent,
				       status = case when status = 'REMOVED' then 'REMOVED'
				                     when cast(:enforceEnd as date) < :today then 'ENDED' else 'ACTIVE' end,
				       updated_at = :now
				 where id = :id
				""", params);
		replaceReferences(id, f.references());
	}

	public void setContentHash(long id, String contentHash) {
		jdbc.update("update benefit set content_hash = :hash where id = :id", Map.of("id", id, "hash", contentHash));
	}

	public void invalidateDetail(long id) {
		// 스키마 변경 없이 목록 버전 회귀를 기록한다. 다음 상세 수집 후 upsert가 시각을 복원한다.
		jdbc.update("update benefit_raw set last_seen_at = to_timestamp(0) where benefit_id = :id and kind = 'DETAIL'",
				Map.of("id", id));
	}

	public void setVerifiedHash(long id, String hash, OffsetDateTime now) {
		jdbc.update("update benefit set verified_hash = :hash, updated_at = :now where id = :id",
				Map.of("id", id, "hash", hash, "now", now));
	}

	/** 이전 버전의 가짜 AI 자동 확정만 회수한다. 값이 전부 null인 수동 승인도 출처 표식으로 보존한다. */
	public int revokeFakeVerifications() {
		return jdbc.getJdbcTemplate().update("""
			update benefit b set verified_hash = null
			 where b.verified_hash is not null
			   and exists (select 1 from benefit_eligibility e
			                join ai_extraction x on x.id = e.extraction_id
			                where e.benefit_id = b.id and x.model = 'rule-based-fake'
			                  and not exists (select 1 from jsonb_each_text(e.field_sources) field
			                                  where field.value = 'MANUAL'))
			""");
	}

	/** 이번 목록에 없는 혜택을 REMOVED로. 새로 REMOVED가 된 id를 돌려준다 */
	public List<Long> markRemoved(Source source, Collection<String> seenSourceIds, OffsetDateTime now) {
		return jdbc.getJdbcTemplate().query(con -> {
			var ps = con.prepareStatement("""
					update benefit set status = 'REMOVED', updated_at = ?
					 where source = ? and status <> 'REMOVED' and not (source_id = any (?))
					returning id
					""");
			Array ids = con.createArrayOf("varchar", seenSourceIds.toArray());
			ps.setObject(1, now);
			ps.setString(2, source.name());
			ps.setArray(3, ids);
			return ps;
		}, (rs, i) -> rs.getLong(1));
	}

	/**
	 * 상세(또는 지원조건)를 새로 받아야 하는 혜택. 시니어 우선, 그다음 id 순.
	 * 해당 종류 raw가 없거나, 마지막으로 받은 뒤에 목록 내용이 바뀐(새 LIST raw가 생긴) 것.
	 */
	public List<Target> findRefreshTargets(Source source, RawKind kind, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("source", source.name())
				.addValue("kind", kind.name())
				.addValue("limit", limit);
		return jdbc.query("""
				select b.id, b.source_id
				  from benefit b
				  join lateral (select max(first_seen_at) as fs from benefit_raw
				                 where benefit_id = b.id and kind = 'LIST') l on true
				  left join lateral (select max(last_seen_at) as ls from benefit_raw
				                      where benefit_id = b.id and kind = :kind) d on true
				 where b.source = :source and b.status <> 'REMOVED'
			   and (d.ls is null or l.fs > d.ls)
				 order by %s desc, b.id
				 limit :limit
				""".formatted(SENIOR_PRIORITY), params, (rs, i) -> new Target(rs.getLong("id"), rs.getString("source_id")));
	}

	public int countRefreshTargets(Source source, RawKind kind) {
		return findRefreshTargets(source, kind, Integer.MAX_VALUE).size();
	}

	public List<GroupMember> loadGroupMembers() {
		return jdbc.query("""
				select id, source, name, ctpv_name, sgg_name, agency_name, group_id
				  from benefit where status <> 'REMOVED'
				""", (rs, i) -> new GroupMember(rs.getLong("id"), Source.valueOf(rs.getString("source")),
				rs.getString("name"), rs.getString("ctpv_name"), rs.getString("sgg_name"),
				rs.getString("agency_name"), rs.getLong("group_id")));
	}

	/** 독립 그룹은 중단 후 재시작해도 다시 병합 대상으로 찾는다. */
	public List<Long> findUngroupedBenefitIds() {
		return jdbc.queryForList("""
			select b.id from benefit b
			 where b.status <> 'REMOVED'
			   and not exists (select 1 from benefit other where other.group_id = b.group_id and other.id <> b.id)
			 order by b.id
			""", Map.of(), Long.class);
	}

	public List<String> lifeCodes(long id) {
		return jdbc.queryForList(
				"select code from benefit_tag where benefit_id = :id and tag_type = 'LIFE' order by code",
				Map.of("id", id), String.class);
	}

	/** 그룹 from의 혜택을 모두 그룹 to로 옮기고 빈 그룹을 지운다. 원천 행은 지우지 않는다. 대표는 호출한 쪽이 다시 고른다 */
	public void mergeGroups(long from, long to, OffsetDateTime now) {
		if (from == to) {
			return;
		}
		Map<String, Object> params = Map.of("from", from, "to", to, "now", now);
		jdbc.update("update benefit set group_id = :to, updated_at = :now where group_id = :from", params);
		jdbc.update("delete from benefit_group where id = :from", params);
	}

	/**
	 * 그룹 대표를 다시 고른다: ACTIVE → ENDED → REMOVED, 같은 상태면 NATIONAL > LOCAL > GOV24 (ADR 0002 §3).
	 */
	public int recomputePrimaries() {
		return jdbc.getJdbcTemplate().update("""
				update benefit_group g
				   set primary_benefit_id = p.id, updated_at = now()
				  from (select distinct on (group_id) group_id, id
				          from benefit
				         order by group_id,
				                  case status when 'ACTIVE' then 0 when 'ENDED' then 1 else 2 end,
				                  case source when 'NATIONAL' then 0 when 'LOCAL' then 1 else 2 end,
				                  id) p
				 where g.id = p.group_id and g.primary_benefit_id is distinct from p.id
				""");
	}

	private void replaceTags(long id, List<TagCode> tags) {
		jdbc.update("delete from benefit_tag where benefit_id = :id", Map.of("id", id));
		if (tags.isEmpty()) {
			return;
		}
		SqlParameterSource[] batch = tags.stream().distinct()
				.map(t -> new MapSqlParameterSource().addValue("id", id).addValue("type", t.type()).addValue("code", t.code()))
				.toArray(SqlParameterSource[]::new);
		jdbc.batchUpdate("insert into benefit_tag (benefit_id, tag_type, code) values (:id, :type, :code)", batch);
	}

	private void replaceReferences(long id, List<Reference> refs) {
		jdbc.update("delete from benefit_reference where benefit_id = :id", Map.of("id", id));
		if (refs.isEmpty()) {
			return;
		}
		SqlParameterSource[] batch = new SqlParameterSource[refs.size()];
		for (int i = 0; i < refs.size(); i++) {
			Reference r = refs.get(i);
			batch[i] = new MapSqlParameterSource().addValue("id", id).addValue("type", r.type())
					.addValue("name", r.name()).addValue("value", r.value()).addValue("order", i);
		}
		jdbc.batchUpdate("""
				insert into benefit_reference (benefit_id, ref_type, name, value, sort_order)
				values (:id, :type, :name, :value, :order)
				""", batch);
	}

	private static MapSqlParameterSource listParams(ListFields f) {
		return new MapSqlParameterSource()
				.addValue("name", f.name())
				.addValue("summary", f.summary())
				.addValue("sourceUrl", f.sourceUrl())
				.addValue("agencyName", f.agencyName())
				.addValue("departmentName", f.departmentName())
				.addValue("ctpvName", f.ctpvName())
				.addValue("sggName", f.sggName())
				.addValue("supportCycle", f.supportCycle())
				.addValue("provisionType", f.provisionType())
				.addValue("applyMethodName", f.applyMethodName())
				.addValue("onlineApply", f.onlineApply())
				.addValue("contact", f.contact())
				.addValue("sourceRegisteredOn", f.sourceRegisteredOn())
				.addValue("sourceModifiedAt", f.sourceModifiedAt());
	}

}
