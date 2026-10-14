package com.silverhelper.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthServiceTests {
    @Test void 리프레시_회전_후_옛_토큰은_401이고_새_토큰은_유효하다() throws Exception {
        UserStore users=mock(UserStore.class);
        StringRedisTemplate redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        JwtEncoder encoder=mock(JwtEncoder.class);
        Environment env=mock(Environment.class);
        Map<String,String> saved=new ConcurrentHashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call->{ saved.put(call.getArgument(0),call.getArgument(1)); return null; })
                .when(values).set(anyString(),anyString(),any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(call->saved.remove((String)call.getArgument(0)));
        when(users.find(7)).thenReturn(Optional.of(new UserStore.User(7,"tester",null,"USER")));
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("signed").header("alg","HS256").subject("7").build());
        AuthService service=new AuthService(users,redis,encoder,env);
        String old=service.issue(new UserStore.User(7,"tester",null,"USER")).cookie().getValue();
        String replacement=service.refresh(old).cookie().getValue();
        assertNotEquals(old,replacement);
        assertTrue(saved.keySet().stream().allMatch(key->key.startsWith("auth:refresh:") && !key.contains(replacement)));
        var mvc=MockMvcBuilders.standaloneSetup(new AuthController(service)).build();
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken",old)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken",replacement)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value("signed"));
    }

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
