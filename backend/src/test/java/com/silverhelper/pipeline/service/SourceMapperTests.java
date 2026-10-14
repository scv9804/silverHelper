package com.silverhelper.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.client.Gov24JsonParser;
import com.silverhelper.pipeline.client.SourcePage;
import com.silverhelper.pipeline.client.SourceRecord;
import com.silverhelper.pipeline.client.WelfareXmlParser;
import com.silverhelper.pipeline.domain.DetailFields;
import com.silverhelper.pipeline.domain.ListFields;
import com.silverhelper.pipeline.domain.Reference;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.domain.TagCode;
import com.silverhelper.pipeline.support.Fixtures;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SourceMapperTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void 중앙부처_목록을_컬럼과_태그로_바꾼다() {
		SourceRecord item = WelfareXmlParser.parseList(Source.NATIONAL, Fixtures.bytes("national-list.xml")).items().getFirst();

		ListFields f = SourceMapper.listFields(Source.NATIONAL, item);

		assertThat(f.name()).isEqualTo("기초연금");
		assertThat(f.agencyName()).isEqualTo("보건복지부");
		assertThat(f.onlineApply()).isTrue();
		assertThat(f.contact()).isEqualTo("1355");
		assertThat(f.sourceRegisteredOn()).isEqualTo(LocalDate.of(2021, 9, 3));
		assertThat(f.ctpvName()).isNull();
		assertThat(f.tags()).contains(new TagCode("LIFE", "006"), new TagCode("HOUSEHOLD", "050"), new TagCode("THEME", "030"));
	}

	@Test
	void 중앙부처_상세에서_기준연도_원문_참고자료를_꺼낸다() {
		SourceRecord detail = WelfareXmlParser.parseDetail(Source.NATIONAL, Fixtures.bytes("national-detail-WLF00001164.xml"));

		DetailFields f = SourceMapper.detailFields(Source.NATIONAL, detail);

		assertThat(f.baseYear()).isEqualTo((short) 2026);
		assertThat(f.targetContent()).contains("만 65세 이상");
		assertThat(f.applyContent()).contains("신청기관연락처목록: 거주지 읍/면/동 주민센터");
		assertThat(f.references()).extracting(Reference::type).contains("CONTACT", "SITE", "LAW", "FORM", "APPLY");
		assertThat(f.references()).contains(new Reference("LAW", "기초연금법", null));
	}

	@Test
	void 지자체_시행종료일_99991231은_NULL이다() {
		SourceRecord detail = WelfareXmlParser.parseDetail(Source.LOCAL, Fixtures.bytes("local-detail-WLF00002281.xml"));

		DetailFields f = SourceMapper.detailFields(Source.LOCAL, detail);

		assertThat(f.enforceStartDate()).isEqualTo(LocalDate.of(2013, 1, 1));
		assertThat(f.enforceEndDate()).isNull();
		assertThat(f.contact()).isEqualTo("보성군청 사회복지과 061-850-5075");
		assertThat(f.criteriaContent()).contains("65세 이상의 세대");
	}

	@Test
	void 지자체_목록의_지역과_최종수정일() {
		SourceRecord item = WelfareXmlParser.parseList(Source.LOCAL, Fixtures.bytes("local-list.xml")).items().getFirst();

		ListFields f = SourceMapper.listFields(Source.LOCAL, item);

		assertThat(f.ctpvName()).isEqualTo("경기도");
		assertThat(f.sggName()).isEqualTo("양주시");
		assertThat(f.sourceModifiedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(f.tags()).doesNotContain(new TagCode("THEME", "130"));
		ListFields health = SourceMapper.listFields(Source.LOCAL,
				WelfareXmlParser.parseList(Source.LOCAL, Fixtures.bytes("local-list.xml")).items().get(1));
		assertThat(health.tags()).doesNotContain(new TagCode("THEME", "130"));
	}

	@Test
	void 정부24_시군구_사업은_소관기관명에서_지역을_뽑는다() {
		SourcePage page = Gov24JsonParser.parse(jsonMapper, 200, Fixtures.text("gov24-list.json"));

		ListFields senior = SourceMapper.listFields(Source.GOV24, page.items().get(1));
		ListFields national = SourceMapper.listFields(Source.GOV24, page.items().get(0));

		assertThat(senior.ctpvName()).isEqualTo("서울특별시");
		assertThat(senior.sggName()).isEqualTo("종로구");
		assertThat(senior.sourceModifiedAt().toString()).startsWith("2026-08-03T10:58:59+09:00");
		assertThat(national.ctpvName()).isNull();
		assertThat(national.tags()).contains(new TagCode("THEME", "120"));
		assertThat(SourceMapper.gov24Region("광역시도", "서울특별시")).containsExactly("서울특별시", null);
		assertThat(SourceMapper.gov24Region("시군구", "경상북도 경주시")).containsExactly("경상북도", "경주시");
		assertThat(SourceMapper.gov24Region("시군구", "광주광역시 북구"))
				.containsExactly("전남광주통합특별시", "북구");
		assertThat(SourceMapper.gov24Region("시군구", "전라남도 순천시"))
				.containsExactly("전남광주통합특별시", "순천시");
		assertThat(SourceMapper.gov24Region("광역시도", "광주광역시"))
				.containsExactly("전남광주통합특별시", null);
		assertThat(SourceMapper.gov24Region("광역시도", "전라남도"))
				.containsExactly("전남광주통합특별시", null);
		assertThat(SourceMapper.gov24Region("시군구", "경주시")).containsExactly("지역 확인 필요", null);
		assertThat(SourceMapper.gov24Region("시군구", "알 수 없는 기관")).containsExactly("지역 확인 필요", null);
	}

	@Test
	void 지자체_LIST의_구_시도명만_정규화하고_기관_원문은_유지한다() {
		SourceRecord old = WelfareXmlParser.parseList(Source.LOCAL, Fixtures.bytes("local-list.xml")).items().getFirst();
		Map<String, Object> fields = new java.util.HashMap<>(old.fields());
		fields.put("ctpvNm", "전라남도");
		fields.put("sggNm", "순천시");
		ListFields mapped = SourceMapper.listFields(Source.LOCAL, new SourceRecord(fields, old.rawBody()));
		assertThat(mapped.ctpvName()).isEqualTo("전남광주통합특별시");
		assertThat(mapped.sggName()).isEqualTo("순천시");
		assertThat(mapped.agencyName()).isEqualTo("전라남도 순천시");
	}

	@Test
	void 정부24는_개인이나_가구_대상만_받는다() {
		assertThat(SourceMapper.accepts(Source.GOV24, record("1", "a", "개인"))).isTrue();
		assertThat(SourceMapper.accepts(Source.GOV24, record("1", "a", "개인||법인/시설/단체"))).isTrue();
		assertThat(SourceMapper.accepts(Source.GOV24, record("1", "a", "가구"))).isTrue();
		assertThat(SourceMapper.accepts(Source.GOV24, record("1", "a", "소상공인||법인/시설/단체"))).isFalse();
		assertThat(SourceMapper.accepts(Source.GOV24, record("1", "a", null))).isFalse();
	}

	@Test
	void AI_입력은_사람이_읽는_항목명으로() {
		SourceRecord detail = WelfareXmlParser.parseDetail(Source.NATIONAL, Fixtures.bytes("national-detail-WLF00001164.xml"));

		var text = SourceMapper.sourceText(Source.NATIONAL, detail.fields());

		assertThat(text.fields()).containsKeys("서비스명", "지원대상", "선정기준", "지원내용");
		assertThat(text.get("지원대상")).doesNotContain("\r");
	}

	private static SourceRecord record(String id, String name, String users) {
		Map<String, Object> fields = new java.util.HashMap<>(Map.of("서비스ID", id, "서비스명", name));
		if (users != null) {
			fields.put("사용자구분", users);
		}
		return new SourceRecord(fields, "{}");
	}

}
