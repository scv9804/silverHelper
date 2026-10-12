package com.silverhelper.pipeline.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.support.Fixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// 실제 응답 픽스처(fixtures/public-api)로 A·B·C 목록·상세·오류 응답을 파싱한다 (public-api.md §2~§6)
class PublicApiParserTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void 중앙부처_목록은_servList마다_항목이고_빠진_요소는_필드가_없다() {
		SourcePage page = WelfareXmlParser.parseList(Source.NATIONAL, Fixtures.bytes("national-list.xml"));

		assertThat(page.totalCount()).isEqualTo(461);
		assertThat(page.items()).hasSize(3);
		SourceRecord pension = page.items().getFirst();
		assertThat(pension.text("servId")).isEqualTo("WLF00001164");
		assertThat(pension.text("lifeArray")).isEqualTo("노년");
		assertThat(pension.text("servDtlLink")).contains("wlfareInfoId=WLF00001164&wlfareInfoReldBztpCd=01");
		// 생애주기가 비어 있는 서비스는 요소 자체가 없다
		assertThat(page.items().get(1).fields()).doesNotContainKey("lifeArray");
		assertThat(page.items().get(2).text("srvPvsnNm")).isEqualTo("현금지급,현금대여(융자),현물지급,프로그램/서비스(서비스),자원봉사");
		assertThat(pension.rawBody()).startsWith("<servList>").contains("<servId>WLF00001164</servId>");
	}

	@Test
	void 중앙부처_상세의_반복_요소는_1건이어도_리스트다() {
		SourceRecord detail = WelfareXmlParser.parseDetail(Source.NATIONAL, Fixtures.bytes("national-detail-WLF00001164.xml"));

		assertThat(detail.text("crtrYr")).isEqualTo("2026");
		assertThat(detail.text("tgtrDtlCn")).contains("만 65세 이상").contains("\r");
		assertThat(detail.list("applmetList")).hasSize(8);
		assertThat(detail.list("baslawList")).hasSize(1);
		assertThat(detail.list("baslawList").getFirst()).containsEntry("servSeDetailNm", "기초연금법");
		assertThat(detail.fields()).doesNotContainKeys("resultCode", "resultMessage");
	}

	@Test
	void 지자체_목록과_상세를_읽는다() {
		SourcePage page = WelfareXmlParser.parseList(Source.LOCAL, Fixtures.bytes("local-list.xml"));
		assertThat(page.totalCount()).isEqualTo(4821);
		assertThat(page.items()).extracting(r -> r.text("servId")).containsExactly("WLF00005610", "WLF00006781", "WLF00005600");
		assertThat(page.items().get(1).text("sggNm")).isNull();

		SourceRecord detail = WelfareXmlParser.parseDetail(Source.LOCAL, Fixtures.bytes("local-detail-WLF00002281.xml"));
		assertThat(detail.text("enfcEndYmd")).isEqualTo("99991231");
		assertThat(detail.text("aplyMtdCn")).isNull();
		assertThat(detail.list("inqplCtadrList").getFirst()).containsEntry("wlfareInfoReldCn", "061-850-5075");
	}

	@Test
	void 게이트웨이_오류는_GATEWAY_예외다() {
		assertThatThrownBy(() -> WelfareXmlParser.parseList(Source.NATIONAL, Fixtures.bytes("gateway-error-unregistered-key.xml")))
				.isInstanceOfSatisfying(PublicApiException.class, e -> {
					assertThat(e.getKind()).isEqualTo(PublicApiException.Kind.GATEWAY);
					assertThat(e.getCode()).isEqualTo("30");
					assertThat(e.stopsSource()).isTrue();
					assertThat(e.getMessage()).contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
				});
	}

	@Test
	void 정상_구조의_resultCode_오류는_RESULT_예외다() {
		assertThatThrownBy(() -> WelfareXmlParser.parseList(Source.NATIONAL, Fixtures.bytes("national-error-invalid-param.xml")))
				.isInstanceOfSatisfying(PublicApiException.class, e -> {
					assertThat(e.getKind()).isEqualTo(PublicApiException.Kind.RESULT);
					assertThat(e.getCode()).isEqualTo("10");
					assertThat(e.stopsSource()).isFalse();
				});
	}

	@Test
	void 코드_22는_한도_초과_예외다() {
		String xml = Fixtures.text("gateway-error-unregistered-key.xml")
				.replace("<returnReasonCode>30</returnReasonCode>", "<returnReasonCode>22</returnReasonCode>")
				.replace("SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");

		assertThatThrownBy(() -> WelfareXmlParser.parseDetail(Source.LOCAL, xml.getBytes()))
				.isInstanceOf(QuotaExceededException.class);
	}

	@Test
	void XML이_아니면_PARSE_예외다() {
		assertThatThrownBy(() -> WelfareXmlParser.parseList(Source.LOCAL, "<html>oops".getBytes()))
				.isInstanceOfSatisfying(PublicApiException.class, e -> assertThat(e.getKind()).isEqualTo(PublicApiException.Kind.PARSE));
	}

	@Test
	void 외부_엔티티는_읽지_않는다() {
		String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><wantedDtl><servId>&e;</servId></wantedDtl>";

		assertThatThrownBy(() -> WelfareXmlParser.parseDetail(Source.LOCAL, xxe.getBytes()))
				.isInstanceOf(PublicApiException.class);
	}

	@Test
	void 정부24_목록은_null_필드를_빼고_matchCount를_건수로_쓴다() {
		SourcePage page = Gov24JsonParser.parse(jsonMapper, 200, Fixtures.text("gov24-list.json"));

		assertThat(page.totalCount()).isEqualTo(3);
		assertThat(page.items()).hasSize(3);
		SourceRecord senior = page.items().get(1);
		assertThat(senior.text("서비스ID")).isEqualTo("300000000105");
		assertThat(senior.text("소관기관명")).isEqualTo("서울특별시 종로구");
		assertThat(senior.fields()).doesNotContainKey("선정기준");
		assertThat(senior.fields().get("조회수")).isEqualTo(1613);
	}

	@Test
	void 정부24_지원조건의_연령은_숫자_플래그는_Y_또는_없음() {
		SourcePage page = Gov24JsonParser.parse(jsonMapper, 200, Fixtures.text("gov24-conditions.json"));

		SourceRecord ltc = page.items().get(1);
		assertThat(ltc.text("서비스ID")).isEqualTo("135200005031");
		assertThat(ltc.fields().get("JA0110")).isEqualTo(19);
		assertThat(ltc.fields().get("JA0328")).isEqualTo("Y");
		assertThat(ltc.fields()).doesNotContainKey("JA0404");
	}

	@Test
	void 정부24_상세를_읽는다() {
		SourcePage page = Gov24JsonParser.parse(jsonMapper, 200, Fixtures.text("gov24-detail-135200005031.json"));

		assertThat(page.items().getFirst().text("법령")).isEqualTo("노인장기요양보험법(제40조, 제4항)");
	}

	@Test
	void 정부24_인증_오류는_HTTP_200이어도_GATEWAY_예외다() {
		assertThatThrownBy(() -> Gov24JsonParser.parse(jsonMapper, 200, Fixtures.text("gov24-error-invalid-key.json")))
				.isInstanceOfSatisfying(PublicApiException.class, e -> {
					assertThat(e.getKind()).isEqualTo(PublicApiException.Kind.GATEWAY);
					assertThat(e.getCode()).isEqualTo("-4");
				});
		assertThatThrownBy(() -> Gov24JsonParser.parse(jsonMapper, 401, "{\"code\":-401,\"msg\":\"유효하지 않은 인증키 입니다.\"}"))
				.isInstanceOfSatisfying(PublicApiException.class, e -> assertThat(e.getCode()).isEqualTo("-401"));
	}

}
