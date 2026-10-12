package com.silverhelper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.client.QuotaExceededException;
import com.silverhelper.pipeline.domain.CollectRunStatus;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.CollectService;
import com.silverhelper.pipeline.service.ExtractionService;
import com.silverhelper.pipeline.support.Fixtures;
import com.silverhelper.pipeline.support.PipelineIntegrationTest;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import com.silverhelper.pipeline.service.GroupMerger;
import org.junit.jupiter.api.Test;

// 수집 → 변경 감지 → AI 추출 흐름 (Testcontainers). 공공 API·AI는 픽스처 기반 대체 구현
class CollectPipelineTests extends PipelineIntegrationTest {
	@Autowired GroupMerger groupMerger;

	@Test
	void 중간_빈_페이지는_불완전_수집으로_보고_REMOVED를_막는다() {
		collectService.collect(Source.LOCAL);
		String original = fixtureLocalClient.list();
		fixtureLocalClient.setList(original.replaceAll("<servList>(?:(?!</servList>).)*WLF00005600(?:(?!</servList>).)*</servList>", ""));
		fixtureLocalClient.emptySecondPage();
		CollectService.Report report = collectService.collect(Source.LOCAL);
		assertThat(report.status()).isEqualTo(CollectRunStatus.PARTIAL);
		assertThat(report.counts().itemsRemoved()).isZero();
		assertThat(benefit(Source.LOCAL, "WLF00005600").get("status")).isEqualTo("ACTIVE");
	}

	@Test
	void 목록이_A에서_B를_거쳐_A로_돌아오면_상세를_재수집한다() {
		String original = fixtureNationalClient.list();
		collectService.collect(Source.NATIONAL);
		fixtureNationalClient.setList(original.replace("안정적인 소득기반을", "더 안정적인 소득기반을"));
		clock.advance(Duration.ofHours(1));
		collectService.collect(Source.NATIONAL);
		assertThat(fixtureNationalClient.detailCalls).hasValue(4);
		fixtureNationalClient.setList(original);
		clock.advance(Duration.ofHours(1));
		collectService.collect(Source.NATIONAL);
		assertThat(fixtureNationalClient.detailCalls).hasValue(5);
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("content_hash")).isNotNull();
	}

	@Test
	void 다른_지역기관의_동명_사업은_자동병합하지_않고_후보로_보낸다() {
		collectService.collect(Source.LOCAL);
		collectService.collect(Source.GOV24);
		long local = benefitId(Source.LOCAL, "WLF00005610");
		long gov = benefitId(Source.GOV24, "300000000105");
		jdbc.update("update benefit set name = '동명 사업', ctpv_name = '경기도', sgg_name = '양주시', agency_name = '다른 기관' where id = ?", gov);
		jdbc.update("update benefit set name = '동명 사업' where id = ?", local);
		groupMerger.mergePending();
		assertThat(jdbc.queryForObject("select group_id from benefit where id = ?", Long.class, gov))
				.isNotEqualTo(jdbc.queryForObject("select group_id from benefit where id = ?", Long.class, local));
		assertThat(count("select count(*) from benefit_group_candidate where benefit_id = ? or target_id = ?", gov, gov)).isPositive();
	}

	@Test
	void 과거_가짜_확정은_공개를_회수하고_실제_모델_버전으로_재처리한다() {
		collectAndExtract(Source.NATIONAL);
		long id = benefitId(Source.NATIONAL, "WLF00001164");
		jdbc.update("update ai_extraction set model = 'rule-based-fake' where benefit_id = ?", id);
		assertThat(extractionService.processPending().published()).isZero();
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("verified_hash")).isNull();
		jdbc.update("update ai_extraction set prompt_version = 'rule-v1' where benefit_id = ?", id);
		assertThat(extractionService.processPending().published()).isEqualTo(1);
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("verified_hash"))
				.isEqualTo(benefit(Source.NATIONAL, "WLF00001164").get("content_hash"));
		assertThat(count("select count(*) from ai_extraction where benefit_id = ? and prompt_version = 'test-v1'", id)).isEqualTo(2);
	}

	@Test
	void 병합_전_중단된_독립_그룹은_다음_실행에_DB에서_재탐색한다() {
		collectService.collect(Source.NATIONAL);
		collectService.collect(Source.GOV24);
		long national = benefitId(Source.NATIONAL, "WLF00006298");
		long gov = benefitId(Source.GOV24, "119200000016");
		jdbc.update("update benefit set name = (select name from benefit where id = ?), agency_name = (select agency_name from benefit where id = ?) where id = ?", national, national, gov);
		assertThat(groupMerger.mergePending().merged()).isPositive();
		assertThat(jdbc.queryForObject("select group_id from benefit where id = ?", Long.class, gov))
				.isEqualTo(jdbc.queryForObject("select group_id from benefit where id = ?", Long.class, national));
	}

	@Test
	void 신규_수집_후_같은_내용이면_AI를_다시_부르지_않고_바뀌면_다시_추출한다() {
		// 1) 신규: 목록 3건, 상세 3건, AI 3건 × 2모델
		ExtractionService.Report first = collectAndExtract(Source.NATIONAL);

		assertThat(first.published()).isEqualTo(3);
		assertThat(fixtureNationalClient.detailCalls).hasValue(3);
		assertThat(claudeTestExtractor.calls).hasValue(3);
		assertThat(gptTestExtractor.calls).hasValue(3);
		Map<String, Object> pension = benefit(Source.NATIONAL, "WLF00001164");
		assertThat(pension.get("verified_hash")).isEqualTo(pension.get("content_hash"));
		assertThat(((Number) pension.get("base_year")).intValue()).isEqualTo(2026);
		assertThat(pension.get("source_url").toString()).contains("WLF00001164");
		long pensionId = (long) pension.get("id");
		assertThat(jdbc.queryForObject("select min_age from benefit_eligibility where benefit_id = ?", Integer.class, pensionId))
				.isEqualTo(65);
		assertThat(count("select count(*) from benefit_reference where benefit_id = ? and ref_type = 'LAW'", pensionId)).isEqualTo(1);
		assertThat(count("select count(*) from benefit_tag where benefit_id = ? and tag_type = 'LIFE' and code = '006'", pensionId)).isEqualTo(1);

		// 2) 같은 내용(조회수만 바뀜) 재수집: 상세·AI 호출 없음, 원문 행도 늘지 않음
		fixtureNationalClient.setList(fixtureNationalClient.list().replace("<inqNum>2088145</inqNum>", "<inqNum>2099999</inqNum>"));
		ExtractionService.Report second = collectAndExtract(Source.NATIONAL);

		assertThat(second.processed()).isZero();
		assertThat(fixtureNationalClient.detailCalls).hasValue(3);
		assertThat(claudeTestExtractor.calls).hasValue(3);
		assertThat(count("select count(*) from benefit_raw where benefit_id = ?", pensionId)).isEqualTo(2);

		// 3) 원문 변경: 목록이 바뀐 1건만 상세를 다시 받고 다시 추출한다
		fixtureNationalClient.setList(fixtureNationalClient.list().replace("안정적인 소득기반을", "더 안정적인 소득기반을"));
		fixtureNationalClient.setDetail("WLF00001164", Fixtures.text("national-detail-WLF00001164.xml")
				.replace("만 65세 이상인 어르신으로서", "만 66세 이상인 어르신으로서")
				.replace("연령 : 만 65세이상인 자", "연령 : 만 66세이상인 자"));
		ExtractionService.Report third = collectAndExtract(Source.NATIONAL);

		assertThat(third.processed()).isEqualTo(1);
		assertThat(fixtureNationalClient.detailCalls).hasValue(4);
		assertThat(claudeTestExtractor.calls).hasValue(4);
		assertThat(gptTestExtractor.calls).hasValue(4);
		Map<String, Object> changed = benefit(Source.NATIONAL, "WLF00001164");
		assertThat(changed.get("content_hash")).isNotEqualTo(pension.get("content_hash"));
		assertThat(changed.get("verified_hash")).isEqualTo(changed.get("content_hash"));
		assertThat(jdbc.queryForObject("select min_age from benefit_eligibility where benefit_id = ?", Integer.class, pensionId))
				.isEqualTo(66);
		assertThat(count("select count(*) from ai_extraction where benefit_id = ?", pensionId)).isEqualTo(4);
		assertThat(count("select count(*) from collect_run where source = 'NATIONAL' and status = 'SUCCEEDED'")).isEqualTo(3);
	}

	@Test
	void 목록에서_사라지면_REMOVED와_검토항목_다시_나타나면_ACTIVE() {
		collectAndExtract(Source.LOCAL);
		String original = fixtureLocalClient.list();
		fixtureLocalClient.setList(original.replaceAll("<servList>(?:(?!</servList>).)*WLF00005600(?:(?!</servList>).)*</servList>", ""));

		CollectService.Report report = collectService.collect(Source.LOCAL);

		assertThat(report.counts().itemsRemoved()).isEqualTo(1);
		long id = benefitId(Source.LOCAL, "WLF00005600");
		assertThat(benefit(Source.LOCAL, "WLF00005600").get("status")).isEqualTo("REMOVED");
		assertThat(reviews(id)).singleElement().satisfies(r -> {
			assertThat(r.get("reason")).isEqualTo("SOURCE_REMOVED");
			assertThat(r.get("status")).isEqualTo("PENDING");
		});

		fixtureLocalClient.setList(original);
		collectService.collect(Source.LOCAL);

		assertThat(benefit(Source.LOCAL, "WLF00005600").get("status")).isEqualTo("ACTIVE");
		assertThat(reviews(id)).singleElement().satisfies(r -> assertThat(r.get("status")).isEqualTo("RESOLVED"));
	}

	@Test
	void 지자체_시행종료일_99991231은_NULL이고_지난_종료일은_ENDED() {
		String detail = Fixtures.text("local-detail-WLF00002281.xml");
		fixtureLocalClient.setDetail("WLF00005610", detail.replace("WLF00002281", "WLF00005610"));
		fixtureLocalClient.setDetail("WLF00005600", detail.replace("WLF00002281", "WLF00005600")
				.replace("<enfcEndYmd>99991231</enfcEndYmd>", "<enfcEndYmd>20251231</enfcEndYmd>"));

		collectAndExtract(Source.LOCAL);

		Map<String, Object> open = benefit(Source.LOCAL, "WLF00005610");
		assertThat(open.get("enforce_end_date")).isNull();
		assertThat(open.get("status")).isEqualTo("ACTIVE");
		assertThat(open.get("contact")).isEqualTo("보성군청 사회복지과 061-850-5075");
		Map<String, Object> ended = benefit(Source.LOCAL, "WLF00005600");
		assertThat(ended.get("status")).isEqualTo("ENDED");
		assertThat(ended.get("enforce_end_date").toString()).isEqualTo(LocalDate.of(2025, 12, 31).toString());
		// 종료된 혜택은 AI로 보내지 않는다
		assertThat(count("select count(*) from ai_extraction where benefit_id = ?", (long) ended.get("id"))).isZero();
	}

	@Test
	void 상세는_시니어_우선이고_한도_초과면_멈췄다가_다음_실행에_이어받는다() {
		fixtureNationalClient.failDetail(servId -> fixtureNationalClient.detailCalls.get() > 1
				? new QuotaExceededException(Source.NATIONAL, "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")
				: null);

		CollectService.Report report = collectService.collect(Source.NATIONAL);

		assertThat(report.status()).isEqualTo(CollectRunStatus.PARTIAL);
		assertThat(report.detailsPending()).isEqualTo(2);
		// 노년 생애주기인 기초연금이 먼저 상세를 받았다
		assertThat(count("select count(*) from benefit_raw r join benefit b on b.id = r.benefit_id "
				+ "where r.kind = 'DETAIL' and b.source_id = 'WLF00001164'")).isEqualTo(1);
		assertThat(count("select count(*) from benefit_raw where kind = 'DETAIL'")).isEqualTo(1);
		// 상세가 없는 혜택은 AI 대상이 아니다
		assertThat(extractionService.processPending().processed()).isEqualTo(1);

		fixtureNationalClient.failDetail(null);
		CollectService.Report next = collectService.collect(Source.NATIONAL);

		assertThat(next.status()).isEqualTo(CollectRunStatus.SUCCEEDED);
		assertThat(count("select count(*) from benefit_raw where kind = 'DETAIL'")).isEqualTo(3);
	}

	@Test
	void 정부24는_개인_가구만_받고_지원조건을_함께_저장한다() {
		collectAndExtract(Source.GOV24);

		Map<String, Object> senior = benefit(Source.GOV24, "300000000105");
		assertThat(senior.get("ctpv_name")).isEqualTo("서울특별시");
		assertThat(senior.get("sgg_name")).isEqualTo("종로구");
		assertThat(senior.get("target_content").toString()).contains("65세 이상");
		assertThat(count("select count(*) from benefit where source = 'GOV24'")).isEqualTo(3);
		assertThat(count("select count(*) from benefit_raw where kind = 'CONDITIONS'")).isEqualTo(3);
		// 3건이라 전체 페이지(1회)보다 대상이 많아 한 번에 받는다
		assertThat(fixtureGov24Client.singleConditionCalls).hasValue(0);
		// 구조화 연령(JA0110=65)과 AI 결과(65세 이상)가 같아 출처가 STRUCTURED
		String sources = jdbc.queryForObject("select field_sources::text from benefit_eligibility where benefit_id = ?",
				String.class, (long) senior.get("id"));
		assertThat(sources).contains("\"minAge\": \"STRUCTURED\"");
	}

	@Test
	void 사용자구분에_개인_가구가_없으면_수집하지_않는다() {
		fixtureGov24Client.setList(fixtureGov24Client.list().replace("\"사용자구분\": \"개인||법인/시설/단체\"", "\"사용자구분\": \"법인/시설/단체\""));

		collectService.collect(Source.GOV24);

		assertThat(count("select count(*) from benefit where source = 'GOV24'")).isEqualTo(2);
		assertThat(count("select count(*) from benefit where source_id = '119200000016'")).isZero();
	}

	@Test
	void 이름_부처_지역이_같으면_자동_병합하고_이름만_같으면_후보로_올린다() {
		collectService.collect(Source.NATIONAL);
		String gov24 = fixtureGov24Client.list()
				// 중앙부처 사업과 이름·부처가 같은 정부24 사업 → 자동 병합
				.replace("\"서비스명\": \"원양어업 경영자금 지원\"", "\"서비스명\": \"자동차사고 피해자 지원사업\"")
				.replace("\"소관기관명\": \"해양수산부\"", "\"소관기관명\": \"국토교통부\"")
				// 이름은 같지만 시군구 사업 → 후보
				.replace("\"서비스명\": \"홀몸 어르신 건강음료 지원\"", "\"서비스명\": \"기초연금\"");
		fixtureGov24Client.setList(gov24);

		collectService.collect(Source.GOV24);

		Map<String, Object> national = benefit(Source.NATIONAL, "WLF00006298");
		Map<String, Object> merged = benefit(Source.GOV24, "119200000016");
		assertThat(merged.get("group_id")).isEqualTo(national.get("group_id"));
		assertThat(jdbc.queryForObject("select primary_benefit_id from benefit_group where id = ?", Long.class, national.get("group_id")))
				.isEqualTo(national.get("id"));

		long candidateId = benefitId(Source.GOV24, "300000000105");
		assertThat(benefit(Source.GOV24, "300000000105").get("group_id")).isNotEqualTo(benefit(Source.NATIONAL, "WLF00001164").get("group_id"));
		assertThat(reviews(candidateId)).singleElement().satisfies(r -> {
			assertThat(r.get("reason")).isEqualTo("GROUP_CANDIDATE");
			assertThat(r.get("group_candidate_id")).isNotNull();
		});
	}

}
