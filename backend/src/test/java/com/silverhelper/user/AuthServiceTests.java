package com.silverhelper.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AuthServiceTests {
    @Test void 일회용_코드는_원문_대신_해시로_저장하고_한_번만_교환된다() {
        UserStore users=mock(UserStore.class);
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        JwtEncoder encoder=mock(JwtEncoder.class);
        Environment env=mock(Environment.class);
        when(env.matchesProfiles("local","test")).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        when(users.find(7)).thenReturn(Optional.of(new UserStore.User(7,"tester",null,"USER")));
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("signed").header("alg","HS256").subject("7").build());
        AuthService service=new AuthService(users,redis,encoder,env);
        String code=service.authorizationCode(7);
        verify(values).set(startsWith("auth:code:"),eq("7"),any(Duration.class));
        assertFalse(code.isBlank());
        when(redis.opsForValue().getAndDelete(anyString())).thenReturn("7",(String)null);
        AuthService.Issued issued=service.exchange(code);
        assertEquals("signed",issued.token().accessToken());
        assertEquals("Bearer",issued.token().tokenType());
        assertThrows(AuthService.InvalidToken.class,()->service.exchange(code));
    }
}
