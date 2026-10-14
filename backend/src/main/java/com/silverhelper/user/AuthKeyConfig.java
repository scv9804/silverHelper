package com.silverhelper.user;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class AuthKeyConfig {
    @Bean
    SecretKey authKey(@Value("${app.auth.jwt-secret:}") String secret, Environment env) {
        if (secret.getBytes(StandardCharsets.UTF_8).length<32) {
            if (env.matchesProfiles("prod") || !env.matchesProfiles("local", "test"))
                throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
            byte[] bytes=new byte[32];
            new SecureRandom().nextBytes(bytes);
            return new SecretKeySpec(bytes,"HmacSHA256");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
    }
}
