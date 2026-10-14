package com.silverhelper.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.ai.anthropic.api-key=test-key", "spring.ai.openai.api-key=test-key"})
@Transactional
class UserStoreRegionTests {
    @Autowired UserStore store;
    @Autowired JdbcTemplate jdbc;

    @Test void 저장된_구_시도명은_현재_이름으로_읽고_새_프로필도_정규화한다() {
        long userId = jdbc.queryForObject(
                "insert into user_account(display_name,role) values ('test','USER') returning id", Long.class);
        long oldId = jdbc.queryForObject("""
                insert into parent_profile(user_id,name,age,ctpv_name,sgg_name)
                values (?,'기존',75,'전라남도','순천시') returning id
                """, Long.class, userId);
        assertThat(store.profile(userId, oldId).orElseThrow().ctpvName()).isEqualTo("전남광주통합특별시");
        assertThat(jdbc.queryForObject("select ctpv_name from parent_profile where id=?", String.class, oldId))
                .isEqualTo("전라남도");

        UserStore.Profile input = new UserStore.Profile(0, "신규", 75, "광주광역시", "북구",
                null, null, null, null, null, null, null, null, null);
        UserStore.Profile created = store.create(userId, input);
        assertThat(created.ctpvName()).isEqualTo("전남광주통합특별시");
        assertThat(jdbc.queryForObject("select ctpv_name from parent_profile where id=?", String.class, created.id()))
                .isEqualTo("전남광주통합특별시");
    }
}
