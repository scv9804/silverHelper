package com.silverhelper.benefit;

import com.silverhelper.benefit.domain.BenefitEligibility;
import com.silverhelper.benefit.dto.BenefitDtos.Profile;
import com.silverhelper.benefit.service.EligibilityMatcher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EligibilityMatcherTests {
    @Test void 지역_미확인_사업은_전국으로_확정하지_않는다() {
        Profile seoul = new Profile(75, "서울특별시", "종로구", null, null, null,
                null, null, null, null, null, null);
        var result = EligibilityMatcher.match(null, seoul, "지역 확인 필요", null);
        assertThat(result.excluded()).isFalse();
        assertThat(result.level()).isEqualTo("CHECK_NEEDED");
        assertThat(result.checkItems()).contains("사업 지역을 확인하세요.");
    }
    private Profile profile(Integer grade, Boolean noGrade, Integer income, Boolean pension) {
        return new Profile(78,"서울특별시","강서구",grade,noGrade,income,null,null,pension,null,null,null);
    }
    @Test void 조건없음과_지역() {
        assertThat(EligibilityMatcher.match(null,profile(null,null,null,null),null,null).level()).isEqualTo("ELIGIBLE");
        assertThat(EligibilityMatcher.match(null,profile(null,null,null,null),"서울특별시","강서구").excluded()).isFalse();
        assertThat(EligibilityMatcher.match(null,profile(null,null,null,null),"부산광역시",null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(null,profile(null,null,null,null),"서울특별시","노원구").excluded()).isTrue();
    }
    @Test void 만족_위반_모름() {
        BenefitEligibility e = new BenefitEligibility();
        e.requiresBasicPension = true;
        assertThat(EligibilityMatcher.match(e,profile(null,null,null,true),null,null).level()).isEqualTo("ELIGIBLE");
        assertThat(EligibilityMatcher.match(e,profile(null,null,null,false),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,profile(null,null,null,null),null,null).checkItems()).containsExactly("기초연금 수급 여부를 확인하세요.");
    }
    @Test void 장기요양_세_상태와_소득() {
        BenefitEligibility e = new BenefitEligibility();
        e.ltcRequired = true;
        e.ltcGrades = new Short[]{3};
        e.incomeMaxMedianPct = 50;
        assertThat(EligibilityMatcher.match(e,profile(3,null,45,null),null,null).level()).isEqualTo("ELIGIBLE");
        assertThat(EligibilityMatcher.match(e,profile(2,null,45,null),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,profile(null,true,45,null),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,profile(null,null,45,null),null,null).checkItems()).contains("장기요양 등급을 확인하세요.");
        assertThat(EligibilityMatcher.match(e,profile(3,null,51,null),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,profile(3,null,null,null),null,null).checkItems()).contains("기준 중위소득 비율을 확인하세요.");
    }
    @Test void 연령_경계와_등급_없음_조건() {
        BenefitEligibility e = new BenefitEligibility();
        e.minAge = 78;
        e.maxAge = 78;
        e.ltcRequired = false;
        assertThat(EligibilityMatcher.match(e,profile(null,true,null,null),null,null).level()).isEqualTo("ELIGIBLE");
        assertThat(EligibilityMatcher.match(e,profile(3,null,null,null),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,profile(null,null,null,null),"서울특별시","노원구").excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,new Profile(77,"서울특별시","강서구",null,true,null,null,null,null,null,null,null),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(e,new Profile(79,"서울특별시","강서구",null,true,null,null,null,null,null,null,null),null,null).excluded()).isTrue();
        assertThat(EligibilityMatcher.match(null,new Profile(78,"서울특별시",null,null,null,null,null,null,null,null,null,null),
                "서울특별시","강서구").checkItems()).containsExactly("거주 시군구를 확인하세요.");
    }
}
