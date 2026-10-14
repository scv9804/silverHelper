package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silverhelper.chat.controller.ChatController;
import com.silverhelper.chat.service.ChatRateLimitedException;
import com.silverhelper.chat.service.ChatRateLimiter;
import com.silverhelper.chat.service.ChatService;
import com.silverhelper.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.jwt.Jwt;

class ChatRateLimiterTests {
    @Test
    @SuppressWarnings("unchecked")
    void 사용자별_키를_사용하고_초과하면_429와_재시도_시간을_반환한다() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class),any(List.class),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(60L);
        ChatRateLimiter limiter=new ChatRateLimiter(redis,5,50);
        ChatController controller=new ChatController(mock(ChatService.class),limiter);
        Jwt jwt=Jwt.withTokenValue("token").header("alg","none").subject("user-42").build();

        assertThatThrownBy(() -> controller.ask(jwt,new com.silverhelper.chat.dto.ChatDtos.Question("질문")))
                .isInstanceOf(ChatRateLimitedException.class)
                .satisfies(e -> {
                    ChatRateLimitedException denied=(ChatRateLimitedException)e;
                    assertThat(denied.retryAfterSeconds()).isBetween(1L,60L);
                });
        ArgumentCaptor<List<String>> keys=ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class),keys.capture(),anyString(),anyString(),anyString(),anyString());
        assertThat(keys.getValue()).allMatch(key -> key.contains("user-42"));
        var response=controller.rateLimited(new ChatRateLimitedException(23));
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("23");
        assertThat(response.getBody().error().code()).isEqualTo("CHAT_RATE_LIMITED");
        assertThat(response.getBody().error().message()).isEqualTo(ErrorCode.CHAT_RATE_LIMITED.getMessage());
    }
}
