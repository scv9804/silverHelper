package com.silverhelper.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(14);
    private final UserStore users;
    private final StringRedisTemplate redis;
    private final JwtEncoder encoder;
    private final boolean secureCookie;
    private final SecureRandom random = new SecureRandom();

    public record Token(String accessToken, String tokenType, long expiresIn, UserStore.User user) {}
    public record Issued(Token token, ResponseCookie cookie) {}

    public AuthService(UserStore users, StringRedisTemplate redis, JwtEncoder encoder, Environment environment) {
        this.users=users; this.redis=redis; this.encoder=encoder;
        this.secureCookie=environment.matchesProfiles("prod") || !environment.matchesProfiles("local", "test");
    }
    public String authorizationCode(long userId) {
        String code=secret();
        redis.opsForValue().set("auth:code:"+hash(code),Long.toString(userId),Duration.ofSeconds(60));
        return code;
    }
    public Issued exchange(String code) {
        if (code==null || code.isBlank()) throw new InvalidToken();
        String id=redis.opsForValue().getAndDelete("auth:code:"+hash(code));
        if (id==null) throw new InvalidToken();
        return issue(user(Long.parseLong(id)));
    }
    public Issued refresh(String raw) {
        if (raw==null || raw.isBlank()) throw new InvalidToken();
        String id=redis.opsForValue().getAndDelete("auth:refresh:"+hash(raw));
        if (id==null) throw new InvalidToken();
        return issue(user(Long.parseLong(id)));
    }
    public void logout(String raw) {
        if (raw!=null && !raw.isBlank()) redis.delete("auth:refresh:"+hash(raw));
    }
    public Issued issue(UserStore.User user) {
        if (users.isDev(user.id())) user=new UserStore.User(user.id(),user.displayName(),user.email(),"USER");
        Instant now=Instant.now();
        JwtClaimsSet claims=JwtClaimsSet.builder().subject(Long.toString(user.id())).claim("role",user.role())
                .id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plus(ACCESS_LIFETIME)).build();
        String access=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        String refresh=secret();
        redis.opsForValue().set("auth:refresh:"+hash(refresh),Long.toString(user.id()),REFRESH_LIFETIME);
        return new Issued(new Token(access,"Bearer",ACCESS_LIFETIME.toSeconds(),user),cookie(refresh,REFRESH_LIFETIME));
    }
    public ResponseCookie clearCookie() { return cookie("",Duration.ZERO); }
    private ResponseCookie cookie(String value,Duration age) {
        return ResponseCookie.from("refreshToken",value).httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path("/api/auth").maxAge(age).build();
    }
    public UserStore.User user(long id) { return users.find(id).orElseThrow(InvalidToken::new); }
    private String secret() {
        byte[] bytes=new byte[32];random.nextBytes(bytes);return HexFormat.of().formatHex(bytes);
    }
    private static String hash(String input) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}
        catch (Exception e) {throw new IllegalStateException(e);}
    }
    public static class InvalidToken extends RuntimeException {}
}
