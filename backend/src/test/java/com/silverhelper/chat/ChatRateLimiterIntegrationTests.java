package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverhelper.chat.service.ChatRateLimitedException;
import com.silverhelper.chat.service.ChatRateLimiter;
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
}
