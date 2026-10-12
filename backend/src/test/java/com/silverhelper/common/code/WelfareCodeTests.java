package com.silverhelper.common.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.common.code.WelfareCode.Type;
import org.junit.jupiter.api.Test;

class WelfareCodeTests {

	@Test
	void 응답의_이름_목록을_코드로_바꾼다() {
		assertThat(WelfareCode.fromNames(Type.LIFE, "청년, 중장년,노년"))
				.containsExactly(WelfareCode.LIFE_YOUTH, WelfareCode.LIFE_MIDDLE, WelfareCode.LIFE_SENIOR);
	}

	@Test
	void 가운뎃점_주변_공백이_있어도_찾는다() {
		// 실제 응답에 "임신 · 출산"으로 온다 (fixtures/public-api/national-list.xml)
		assertThat(WelfareCode.fromName(Type.LIFE, "임신 · 출산")).contains(WelfareCode.LIFE_PREGNANCY);
	}

	@Test
	void 같은_이름도_종류가_다르면_다른_코드다() {
		assertThat(WelfareCode.fromName(Type.THEME, "임신·출산")).contains(WelfareCode.THEME_PREGNANCY);
		assertThat(WelfareCode.THEME_PREGNANCY.code()).isEqualTo("080");
	}

	@Test
	void 빈_값이나_모르는_이름은_무시한다() {
		assertThat(WelfareCode.fromNames(Type.LIFE, null)).isEmpty();
		assertThat(WelfareCode.fromNames(Type.LIFE, "노년,외계인")).containsExactly(WelfareCode.LIFE_SENIOR);
	}

	@Test
	void 코드로_찾는다() {
		assertThat(WelfareCode.fromCode(Type.LIFE, "006")).contains(WelfareCode.LIFE_SENIOR);
		assertThat(WelfareCode.fromCode(Type.LTC_GRADE, "6").map(WelfareCode::displayName)).contains("인지지원등급");
	}

}
