package com.silverhelper.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.silverhelper.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.ai.anthropic.api-key=test-key",
        "spring.ai.openai.api-key=test-key"
})
class UserStoreKakaoEmailTests {
    @Autowired UserStore users;
    @Autowired JdbcTemplate jdbc;

    @Test void 기존_카카오_계정의_미검증_주소는_재로그인에서_제거된다() {
        String providerId = "legacy-unverified-" + UUID.randomUUID();
        UserStore.User legacy = users.social("KAKAO", providerId, "사용자", "unverified@example.com");

        UserStore.User updated = users.social("KAKAO", providerId, "사용자", null);

        assertEquals(legacy.id(), updated.id());
        assertNull(updated.email());
        assertNull(emailInDatabase(legacy.id()));
    }

    @Test void 기존_카카오_계정의_검증된_주소는_유지하고_새_검증_주소로_갱신한다() {
        String providerId = "verified-" + UUID.randomUUID();
        UserStore.User original = users.social("KAKAO", providerId, "사용자", "verified@example.com");

        UserStore.User unchanged = users.social("KAKAO", providerId, "사용자", "verified@example.com");
        assertEquals(original.id(), unchanged.id());
        assertEquals("verified@example.com", emailInDatabase(original.id()));

        UserStore.User updated = users.social("KAKAO", providerId, "사용자", "new-verified@example.com");
        assertEquals(original.id(), updated.id());
        assertEquals("new-verified@example.com", updated.email());
        assertEquals("new-verified@example.com", emailInDatabase(original.id()));
    }

    private String emailInDatabase(long id) {
        return jdbc.queryForObject("select email from user_account where id=?", String.class, id);
    }
}
