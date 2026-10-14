package com.silverhelper.user;

import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods=false)
public class AuthSecurity {
    @Bean
    SecretKey authKey(@Value("${app.auth.jwt-secret:}") String secret, Environment env) {
        if (secret.getBytes(StandardCharsets.UTF_8).length<32) {
            if (!env.matchesProfiles("local", "test")) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
            byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
            return new SecretKeySpec(bytes,"HmacSHA256");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
    }
    @Bean JwtEncoder authJwtEncoder(SecretKey authKey) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(authKey));
    }
    @Bean JwtDecoder authJwtDecoder(SecretKey authKey) {
        return NimbusJwtDecoder.withSecretKey(authKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
    @Bean
    @Order(0)
    SecurityFilterChain authFilterChain(HttpSecurity http, JsonMapper mapper,
            ObjectProvider<ClientRegistrationRepository> registrations, AuthenticationSuccessHandler oauthSuccess) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(registrations.getIfAvailable()==null?SessionCreationPolicy.STATELESS:SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(a->a
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/me/**", "/api/me", "/api/chat", "/api/chat/**").authenticated()
                        .requestMatchers("/api/dev/login").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**","/actuator/health","/actuator/info","/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->write(res,mapper,ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req,res,ex)->write(res,mapper,ErrorCode.FORBIDDEN)));
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt->java.util.List.of(new SimpleGrantedAuthority("ROLE_"+jwt.getClaimAsString("role"))));
        http.oauth2ResourceServer(o->o
                .authenticationEntryPoint((req,res,ex)->write(res,mapper,ErrorCode.UNAUTHORIZED))
                .jwt(j->j.jwtAuthenticationConverter(converter)));
        if (registrations.getIfAvailable()!=null) http.oauth2Login(o->o.successHandler(oauthSuccess));
        return http.build();
    }
    private static void write(HttpServletResponse response,JsonMapper mapper,ErrorCode error) throws IOException {
        response.setStatus(error.getStatus().value());response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");mapper.writeValue(response.getOutputStream(),ApiResponse.fail(error));
    }
}
