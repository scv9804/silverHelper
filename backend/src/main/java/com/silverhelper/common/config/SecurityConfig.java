package com.silverhelper.common.config;

import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 1 보안 설정. 로그인은 Phase 2에서 붙인다.
 * - /api/admin/** 는 ADMIN 권한 필요 (지금은 로그인 수단이 없어 항상 401)
 * - 나머지 /api/**, Swagger, health는 공개
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/**").permitAll()
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/info").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().denyAll())
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) -> write(res, jsonMapper, ErrorCode.UNAUTHORIZED))
						.accessDeniedHandler((req, res, ex) -> write(res, jsonMapper, ErrorCode.FORBIDDEN)));
		return http.build();
	}

	private static void write(HttpServletResponse res, JsonMapper jsonMapper, ErrorCode code) throws IOException {
		res.setStatus(code.getStatus().value());
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(res.getOutputStream(), ApiResponse.fail(code));
	}

}
