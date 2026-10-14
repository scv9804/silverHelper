package com.silverhelper.chat.service;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 모든 서버 인스턴스에서 동일하게 적용하는 사용자별 고정 시간창 제한. */
@Service
public class ChatRateLimiter {
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local minute = tonumber(redis.call('GET', KEYS[1]) or '0')
            local day = tonumber(redis.call('GET', KEYS[2]) or '0')
            if day >= tonumber(ARGV[2]) then return 86400 end
            if minute >= tonumber(ARGV[1]) then return 60 end
            redis.call('INCR', KEYS[1])
            redis.call('EXPIREAT', KEYS[1], ARGV[3])
            redis.call('INCR', KEYS[2])
            redis.call('EXPIREAT', KEYS[2], ARGV[4])
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int perMinute;
    private final int perDay;

    public ChatRateLimiter(StringRedisTemplate redis,
            @Value("${app.chat.per-minute:5}") int perMinute,
            @Value("${app.chat.per-day:50}") int perDay) {
        this.redis = redis;
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    public void check(String userId) {
        long now = Instant.now().getEpochSecond();
        long minuteEnd = (now / 60 + 1) * 60;
        long dayEnd = (now / 86400 + 1) * 86400;
        Long denied = redis.execute(CHECK, List.of("chat:minute:" + userId + ":" + now / 60,
                "chat:day:" + userId + ":" + now / 86400),
                Integer.toString(perMinute), Integer.toString(perDay),
                Long.toString(minuteEnd), Long.toString(dayEnd));
        if (denied == null) throw new IllegalStateException("채팅 요청 제한 상태를 확인할 수 없습니다");
        if (denied > 0) {
            long retryAfter = denied == 60 ? minuteEnd - now : dayEnd - now;
            throw new ChatRateLimitedException(retryAfter);
        }
    }
}
