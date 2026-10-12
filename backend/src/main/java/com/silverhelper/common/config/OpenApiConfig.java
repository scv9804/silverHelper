package com.silverhelper.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (/swagger-ui.html). 기준 명세는 docs/specs/openapi.yaml이고, 이것은 구현 확인용이다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI silverHelperOpenApi() {
		return new OpenAPI().info(new Info()
				.title("실버헬퍼 API")
				.version("0.1.0")
				.description("기준 명세: docs/specs/openapi.yaml"));
	}

}
