package com.silverhelper.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.silverhelper.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class UserStoreConcurrencyTests {
    @Autowired UserStore users;
    @Autowired JdbcTemplate jdbc;

    @Test void 같은_소셜_식별자의_동시_첫_로그인은_한_계정으로_수렴한다() throws Exception {
        String providerId="concurrent-"+UUID.randomUUID();
        CountDownLatch start=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->{start.await();return users.social("GOOGLE",providerId,"테스트",null);});
            var second=executor.submit(()->{start.await();return users.social("GOOGLE",providerId,"테스트",null);});
            start.countDown();
            assertEquals(first.get(10,TimeUnit.SECONDS).id(),second.get(10,TimeUnit.SECONDS).id());
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from social_account where provider='GOOGLE' and provider_user_id=?",Integer.class,providerId));
    }
}
