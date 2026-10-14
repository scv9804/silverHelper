package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.domain.RawKind;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 정규화 해시: 조회수 제외, CR·공백·가운뎃점 정규화, 필드 순서 무관 (public-api.md §8)
class ContentHasherTests {

	@Test
	void 원천별_표시와_AI_입력_외_필드는_변경으로_보지_않는다() {
		String a=ContentHasher.hash(Source.GOV24,RawKind.LIST,Map.of("서비스명","노인 지원","inqNum",1));
		String b=ContentHasher.hash(Source.GOV24,RawKind.LIST,Map.of("서비스명","노인 지원","inqNum",999,"임의메타","변경"));
		String c=ContentHasher.hash(Source.GOV24,RawKind.LIST,Map.of("서비스명","다른 지원","inqNum",1));
		assertThat(a).isEqualTo(b).isNotEqualTo(c);
	}

	@Test
	void 조회수만_다르면_같은_해시() {
		assertThat(ContentHasher.hash(Map.of("servNm", "기초연금", "inqNum", "100")))
				.isEqualTo(ContentHasher.hash(Map.of("servNm", "기초연금", "inqNum", "999")));
		assertThat(ContentHasher.hash(Map.of("서비스명", "a", "조회수", 1)))
				.isEqualTo(ContentHasher.hash(Map.of("서비스명", "a", "조회수", 2)));
	}

	@Test
	void CR과_공백과_가운뎃점_표기는_정규화한다() {
		String a = ContentHasher.hash(Map.of("t", "만 65세 이상\r\n어르신  지원", "life", "임신 · 출산"));
		String b = ContentHasher.hash(Map.of("t", "만 65세 이상 어르신 지원", "life", "임신ㆍ출산"));

		assertThat(a).isEqualTo(b).hasSize(64);
	}

	@Test
	void 필드_순서가_달라도_같은_해시() {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("a", "1");
		a.put("b", List.of(Map.of("x", "1")));
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("b", List.of(Map.of("x", "1")));
		b.put("a", "1");

		assertThat(ContentHasher.hash(a)).isEqualTo(ContentHasher.hash(b));
	}

	@Test
	void 내용이_바뀌면_다른_해시() {
		assertThat(ContentHasher.hash(Map.of("t", "65세 이상")))
				.isNotEqualTo(ContentHasher.hash(Map.of("t", "70세 이상")));
		assertThat(ContentHasher.hash(Map.of("a", "1")))
				.isNotEqualTo(ContentHasher.hash(Map.of("a", "1", "b", "2")));
	}

	@Test
	void 빈_값은_없는_필드와_같다() {
		assertThat(ContentHasher.hash(Map.of("a", "1", "b", "  ")))
				.isEqualTo(ContentHasher.hash(Map.of("a", "1")));
	}

}
