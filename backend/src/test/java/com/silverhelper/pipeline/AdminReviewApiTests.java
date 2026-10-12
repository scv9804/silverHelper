package com.silverhelper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.support.PipelineIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// 관리자 검토 API (openapi.yaml /api/admin/reviews/**)
@WithMockUser(username = "admin@test", roles = "ADMIN")
class AdminReviewApiTests extends PipelineIntegrationTest {

	@Autowired
	MockMvc mvc;

	/** 기초연금 1건을 MODEL_MISMATCH(Claude 65, GPT 70)로 검토 대기열에 올리고 검토 id를 돌려준다 */
	private long mismatchReview() {
		claudeTestExtractor.respond(t -> CrossValidationFlowTests.AGE_65);
		gptTestExtractor.respond(t -> CrossValidationFlowTests.AGE_70);
		collectAndExtract(Source.NATIONAL);
		return (long) reviews(benefitId(Source.NATIONAL, "WLF00001164")).getFirst().get("id");
	}

	@Test
	void 대기_목록은_오래된_순이고_사유로_거른다() throws Exception {
		mismatchReview();

		mvc.perform(get("/api/admin/reviews").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content", hasSize(2)))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content[0].benefitName").value("기초연금"))
				.andExpect(jsonPath("$.data.content[0].source").value("NATIONAL"))
				.andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.data.content[0].resolvedAt").isEmpty());

		mvc.perform(get("/api/admin/reviews").param("reason", "SOURCE_REMOVED"))
				.andExpect(jsonPath("$.data.totalElements").value(0));
		mvc.perform(get("/api/admin/reviews").param("size", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 상세는_원문과_두_모델_결과와_차이를_보여준다() throws Exception {
		long id = mismatchReview();

		mvc.perform(get("/api/admin/reviews/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.reason").value("MODEL_MISMATCH"))
				.andExpect(jsonPath("$.data.sourceUrl").value(org.hamcrest.Matchers.containsString("WLF00001164")))
				.andExpect(jsonPath("$.data.sourceText.지원대상").value(org.hamcrest.Matchers.containsString("만 65세 이상")))
				.andExpect(jsonPath("$.data.currentEligibility").isEmpty())
				.andExpect(jsonPath("$.data.extractions", hasSize(2)))
				.andExpect(jsonPath("$.data.extractions[*].provider", hasItem("GPT")))
				.andExpect(jsonPath("$.data.extractions[0].result.minAge").exists())
				.andExpect(jsonPath("$.data.differences[0].field").value("minAge"))
				.andExpect(jsonPath("$.data.differences[0].claude").value(65))
				.andExpect(jsonPath("$.data.differences[0].gpt").value(70))
				.andExpect(jsonPath("$.data.groupCandidate").isEmpty());
	}

	@Test
	void 기준_추출결과로_승인하면_조건이_확정되고_공개된다() throws Exception {
		long id = mismatchReview();
		long gptId = (long) jdbc.queryForMap("select gpt_extraction_id from review_item where id = ?", id).get("gpt_extraction_id");

		mvc.perform(post("/api/admin/reviews/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"baseExtractionId\": " + gptId + ", \"note\": \"원문 재확인\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.resolvedBy").value("admin@test"))
				.andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());

		long benefitId = benefitId(Source.NATIONAL, "WLF00001164");
		Map<String, Object> e = jdbc.queryForMap("select * from benefit_eligibility where benefit_id = ?", benefitId);
		assertThat(e.get("min_age")).isEqualTo(70);
		assertThat(e.get("extraction_id")).isEqualTo(gptId);
		Map<String, Object> b = benefit(Source.NATIONAL, "WLF00001164");
		assertThat(b.get("verified_hash")).isEqualTo(b.get("content_hash"));

		// 이미 처리된 항목은 409
		mvc.perform(post("/api/admin/reviews/{id}/approve", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_RESOLVED"));
		mvc.perform(post("/api/admin/reviews/{id}/reject", id).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void 직접_입력한_조건으로_승인한다() throws Exception {
		long id = mismatchReview();

		mvc.perform(post("/api/admin/reviews/{id}/approve", id).contentType(MediaType.APPLICATION_JSON).content("""
						{"eligibility": {"minAge": 65, "maxAge": null, "ltcRequired": null, "ltcGrades": [1, 2],
						 "incomeMaxMedianPct": null, "requiresBasicLivelihood": null, "requiresNearPoverty": null,
						 "requiresBasicPension": null, "requiresDisability": null, "requiresVeteran": null,
						 "requiresSingleHousehold": null, "conditionsSummary": "만 65세 이상, 소득인정액 기준 이하"}}
						"""))
				.andExpect(status().isOk());

		Map<String, Object> e = jdbc.queryForMap("select min_age, ltc_grades::text as grades, field_sources::text as fs "
				+ "from benefit_eligibility where benefit_id = ?", benefitId(Source.NATIONAL, "WLF00001164"));
		assertThat(e.get("min_age")).isEqualTo(65);
		assertThat(e.get("grades")).isEqualTo("{1,2}");
		assertThat(e.get("fs").toString()).contains("\"minAge\": \"MANUAL\"");
	}

	@Test
	void 가짜_추출을_기준으로_사람이_승인한_조건은_재처리_중에도_공개를_유지한다() throws Exception {
		long reviewId = mismatchReview();
		long benefitId = benefitId(Source.NATIONAL, "WLF00001164");
		long baseId = (long) jdbc.queryForMap("select claude_extraction_id from review_item where id = ?", reviewId)
				.get("claude_extraction_id");
		jdbc.update("update ai_extraction set model = 'rule-based-fake' where id = ?", baseId);

		mvc.perform(post("/api/admin/reviews/{id}/approve", reviewId).contentType(MediaType.APPLICATION_JSON)
						.content("""
						{"baseExtractionId": %d, "eligibility": {"minAge": 68, "conditionsSummary": "관리자가 원문 확인"}}
						""".formatted(baseId)))
				.andExpect(status().isOk());
		String verified = (String) benefit(Source.NATIONAL, "WLF00001164").get("verified_hash");
		assertThat(verified).isNotNull();
		assertThat(extractionService.processPending().processed()).isZero();
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("verified_hash")).isEqualTo(verified);
		assertThat(jdbc.queryForMap("select min_age, field_sources::text as sources from benefit_eligibility where benefit_id = ?", benefitId))
				.satisfies(row -> {
					assertThat(row.get("min_age")).isEqualTo(68);
					assertThat(row.get("sources").toString()).contains("MANUAL");
				});
	}

	@Test
	void 모든_조건이_null인_수동_승인도_가짜_추출_회수에서_제외한다() throws Exception {
		long reviewId = mismatchReview();
		long benefitId = benefitId(Source.NATIONAL, "WLF00001164");
		long baseId = (long) jdbc.queryForMap("select claude_extraction_id from review_item where id = ?", reviewId)
				.get("claude_extraction_id");
		jdbc.update("update ai_extraction set model = 'rule-based-fake' where id = ?", baseId);

		mvc.perform(post("/api/admin/reviews/{id}/approve", reviewId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"baseExtractionId\":" + baseId + ",\"eligibility\":{}}"))
				.andExpect(status().isOk());
		String verified = (String) benefit(Source.NATIONAL, "WLF00001164").get("verified_hash");
		assertThat(verified).isNotNull();
		assertThat(jdbc.queryForObject("select min_age from benefit_eligibility where benefit_id = ?", Integer.class, benefitId))
				.isNull();
		assertThat(jdbc.queryForObject("select field_sources::text from benefit_eligibility where benefit_id = ?",
				String.class, benefitId)).contains("\"_approvalSource\": \"MANUAL\"");

		assertThat(extractionService.processPending().processed()).isZero();
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("verified_hash")).isEqualTo(verified);
		assertThat(count("select count(*) from benefit where id = ? and status = 'ACTIVE' and verified_hash is not null",
				benefitId)).isEqualTo(1);
	}

	@Test
	void 잘못된_조건은_400() throws Exception {
		long id = mismatchReview();

		mvc.perform(post("/api/admin/reviews/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"eligibility\": {\"minAge\": 200}}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/admin/reviews/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"eligibility\": {\"minAge\": 80, \"maxAge\": 70}}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/admin/reviews/{id}/approve", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"baseExtractionId\": 999999}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 반려하면_조건을_바꾸지_않는다() throws Exception {
		long id = mismatchReview();

		mvc.perform(post("/api/admin/reviews/{id}/reject", id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"note\": \"원문 확인 필요\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REJECTED"));

		assertThat(count("select count(*) from benefit_eligibility")).isZero();
		assertThat(benefit(Source.NATIONAL, "WLF00001164").get("verified_hash")).isNull();
		mvc.perform(get("/api/admin/reviews/{id}", id))
				.andExpect(jsonPath("$.data.resolutionNote").value("원문 확인 필요"));
		mvc.perform(get("/api/admin/reviews").param("status", "REJECTED"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void 반려_사유는_필수() throws Exception {
		long id = mismatchReview();

		mvc.perform(post("/api/admin/reviews/{id}/reject", id).contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("note"));
	}

	@Test
	void 없는_검토항목은_404() throws Exception {
		mvc.perform(get("/api/admin/reviews/{id}", 424242))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("REVIEW_NOT_FOUND"));
		mvc.perform(post("/api/admin/reviews/{id}/approve", 424242).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 병합_후보를_승인하면_같은_그룹이_된다() throws Exception {
		collectService.collect(Source.NATIONAL);
		fixtureGov24Client.setList(fixtureGov24Client.list()
				.replace("\"서비스명\": \"홀몸 어르신 건강음료 지원\"", "\"서비스명\": \"기초연금\""));
		collectService.collect(Source.GOV24);
		long gov24Id = benefitId(Source.GOV24, "300000000105");
		long reviewId = (long) reviews(gov24Id).getFirst().get("id");

		mvc.perform(get("/api/admin/reviews/{id}", reviewId))
				.andExpect(jsonPath("$.data.reason").value("GROUP_CANDIDATE"))
				.andExpect(jsonPath("$.data.groupCandidate.targetName").value("기초연금"))
				.andExpect(jsonPath("$.data.groupCandidate.targetSource").value("NATIONAL"))
				.andExpect(jsonPath("$.data.groupCandidate.method").value("NAME_EXACT"));

		mvc.perform(post("/api/admin/reviews/{id}/approve", reviewId).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk());

		Map<String, Object> national = benefit(Source.NATIONAL, "WLF00001164");
		assertThat(benefit(Source.GOV24, "300000000105").get("group_id")).isEqualTo(national.get("group_id"));
		assertThat(jdbc.queryForObject("select primary_benefit_id from benefit_group where id = ?", Long.class, national.get("group_id")))
				.isEqualTo(national.get("id"));
		assertThat(jdbc.queryForObject("select status from benefit_group_candidate", String.class)).isEqualTo("MERGED");
	}

	@Test
	@WithMockUser(roles = "USER")
	void 관리자가_아니면_403() throws Exception {
		mvc.perform(get("/api/admin/reviews"))
				.andExpect(status().isForbidden());
	}

}
