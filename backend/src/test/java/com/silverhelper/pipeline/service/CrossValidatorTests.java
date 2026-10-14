package com.silverhelper.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.domain.EligibilityResult;
import com.silverhelper.pipeline.domain.ReviewReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossValidatorTests {

	@Test
	void 두_결과가_같으면_공개하고_요약문_차이는_무시한다() {
		CrossValidator.Decision d = CrossValidator.decide(age(65, "65세 이상"), age(65, "만 65세 이상 어르신"), StructuredFacts.NONE);

		assertThat(d.publish()).isTrue();
		assertThat(d.fieldSources()).containsEntry("minAge", "AI").containsEntry("conditionsSummary", "AI");
		assertThat(d.fieldSources()).containsEntry("_summaryReview", "원문 확인");
	}

	@Test
	void 요약문의_명시적_나이와_구조화_나이가_다르면_검토한다() {
		var d=CrossValidator.decide(age(65,"70세 이상 지원"),age(65,"70세 이상 지원"),StructuredFacts.NONE);
		assertThat(d.reason()).isEqualTo(ReviewReason.STRUCTURED_MISMATCH);
		assertThat(d.differences()).extracting(CrossValidator.Difference::field).contains("conditionsSummary");
	}

	@Test
	void 등급_순서만_다르면_같은_결과다() {
		EligibilityResult a = new EligibilityResult(null, null, true, List.of(1, 2), null, null, null, null, null, null, null, null);
		EligibilityResult b = new EligibilityResult(null, null, true, List.of(2, 1), null, null, null, null, null, null, null, null);

		assertThat(CrossValidator.decide(a, b, StructuredFacts.NONE).publish()).isTrue();
	}

	@Test
	void 값이_다르면_MODEL_MISMATCH와_차이_목록() {
		CrossValidator.Decision d = CrossValidator.decide(age(65, null), age(70, null), StructuredFacts.NONE);

		assertThat(d.reason()).isEqualTo(ReviewReason.MODEL_MISMATCH);
		assertThat(d.differences()).containsExactly(new CrossValidator.Difference("minAge", 65, 70, null));
	}

	@Test
	void 한쪽이라도_스키마_실패면_SCHEMA_INVALID() {
		assertThat(CrossValidator.decide(age(65, null), null, StructuredFacts.NONE).reason())
				.isEqualTo(ReviewReason.SCHEMA_INVALID);
	}

	@Test
	void 정부24_대상연령과_다르면_STRUCTURED_MISMATCH() {
		StructuredFacts facts = StructuredFacts.ofGov24Conditions(Map.of("서비스ID", "x", "JA0110", 65, "JA0111", 120));

		CrossValidator.Decision d = CrossValidator.decide(age(60, null), age(60, null), facts);

		assertThat(d.reason()).isEqualTo(ReviewReason.STRUCTURED_MISMATCH);
		assertThat(d.differences().getFirst().structured()).isEqualTo(65);
	}

	@Test
	void 구조화_연령과_같으면_출처를_STRUCTURED로_적는다() {
		StructuredFacts facts = StructuredFacts.ofGov24Conditions(Map.of("서비스ID", "x", "JA0110", 65, "JA0111", 120, "JA0404", "Y"));

		CrossValidator.Decision d = CrossValidator.decide(age(65, null), age(65, null), facts);

		assertThat(d.publish()).isTrue();
		assertThat(d.fieldSources()).containsEntry("minAge", "STRUCTURED");
	}

	@Test
	void AI가_연령을_말하지_않으면_구조화_연령과_충돌하지_않는다() {
		// 장기요양 감경은 JA0110=19지만 원문에 연령 언급이 없다 (public-api.md §7)
		StructuredFacts facts = StructuredFacts.ofGov24Conditions(Map.of("서비스ID", "x", "JA0110", 19, "JA0111", 120));

		assertThat(CrossValidator.decide(EligibilityResult.EMPTY, EligibilityResult.EMPTY, facts).publish()).isTrue();
	}

	@Test
	void 생애주기에_노년이_없는데_60세_이상이라고_하면_충돌() {
		StructuredFacts youth = StructuredFacts.ofLifeCodes(List.of("004"));
		StructuredFacts senior = StructuredFacts.ofLifeCodes(List.of("004", "006"));

		assertThat(CrossValidator.decide(age(65, null), age(65, null), youth).reason()).isEqualTo(ReviewReason.STRUCTURED_MISMATCH);
		assertThat(CrossValidator.decide(age(65, null), age(65, null), senior).publish()).isTrue();
		assertThat(CrossValidator.decide(age(65, null), age(65, null), StructuredFacts.ofLifeCodes(List.of())).publish()).isTrue();
	}

	@Test
	void 정부24_1인가구_플래그가_없는데_1인가구_필수면_충돌() {
		StructuredFacts facts = StructuredFacts.ofGov24Conditions(Map.of("서비스ID", "x", "JA0110", 65));
		EligibilityResult single = new EligibilityResult(65, null, null, null, null, null, null, null, null, null, true, null);

		CrossValidator.Decision d = CrossValidator.decide(single, single, facts);

		assertThat(d.reason()).isEqualTo(ReviewReason.STRUCTURED_MISMATCH);
		assertThat(d.differences()).extracting(CrossValidator.Difference::field).containsExactly("requiresSingleHousehold");
	}

	private static EligibilityResult age(Integer minAge, String summary) {
		return new EligibilityResult(minAge, null, null, null, null, null, null, null, null, null, null, summary);
	}

}
