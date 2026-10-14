package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.chat.service.ChatRateLimitedException;
import com.silverhelper.chat.service.ChatRateLimiter;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChatRateLimiterIntegrationTests {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void 사용자별_분당_한도와_일당_한도가_원자적으로_적용된다() {
        var connection=new LettuceConnectionFactory(REDIS.getHost(),REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        try {
            var template=new StringRedisTemplate(connection);
            template.afterPropertiesSet();
            var minute=new ChatRateLimiter(template,2,10);
            minute.check("minute-user");
            minute.check("minute-user");
            assertThatThrownBy(() -> minute.check("minute-user"))
                    .isInstanceOf(ChatRateLimitedException.class);
            minute.check("other-user");

            var day=new ChatRateLimiter(template,3,2);
            day.check("day-user");
            day.check("day-user");
            assertThatThrownBy(() -> day.check("day-user"))
                    .isInstanceOf(ChatRateLimitedException.class)
                    .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                            ((ChatRateLimitedException)e).retryAfterSeconds()).isBetween(1L,86400L));
        } finally {
            connection.destroy();
        }
    }

    @Test
    void 분당_일당_한도를_동시에_초과하면_다음_UTC_일까지_기다리도록_안내한다() {
        var connection=new LettuceConnectionFactory(REDIS.getHost(),REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        try {
            var template=new StringRedisTemplate(connection);
            template.afterPropertiesSet();
            var limiter=new ChatRateLimiter(template,2,2);
            for (int attempt=0;attempt<3;attempt++) {
                long before=Instant.now().getEpochSecond();
                String user="both-limits-"+attempt;
                template.opsForValue().set("chat:minute:"+user+":"+before/60,"2");
                template.opsForValue().set("chat:day:"+user+":"+before/86400,"2");
                ChatRateLimitedException denied=null;
                try {
                    limiter.check(user);
                } catch (ChatRateLimitedException e) {
                    denied=e;
                }
                long after=Instant.now().getEpochSecond();
                if (before/60 != after/60 || before/86400 != after/86400) continue;
                assertThat(denied).isNotNull();
                long dayEnd=(before/86400+1)*86400;
                assertThat(denied.retryAfterSeconds()).isBetween(dayEnd-after,dayEnd-before);
                return;
            }
            throw new AssertionError("안정된 UTC 시간창에서 동시 초과를 확인하지 못했습니다");
        } finally {
            connection.destroy();
        }
    }
}
