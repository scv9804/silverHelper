package com.silverhelper.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverhelper.TestcontainersConfiguration;
import com.silverhelper.common.exception.BusinessException;
import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 공통 응답 형식, 전역 예외 처리, 보안 규칙이 openapi.yaml 약속대로 동작하는지 확인한다
@Import({TestcontainersConfiguration.class, CommonWebTests.TestApi.class})
@SpringBootTest(properties = {
		"spring.ai.anthropic.api-key=test-key",
		"spring.ai.openai.api-key=test-key"
})
@AutoConfigureMockMvc
class CommonWebTests {

	@Autowired
	MockMvc mvc;

	@Test
	void 성공_응답은_success와_data를_담는다() throws Exception {
		mvc.perform(get("/api/test/ok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.hello").value("world"))
				.andExpect(jsonPath("$.error").isEmpty());
	}

	@Test
	void 비즈니스_예외는_오류코드의_상태와_코드로_바뀐다() throws Exception {
		mvc.perform(get("/api/test/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").isEmpty())
				.andExpect(jsonPath("$.error.code").value("BENEFIT_NOT_FOUND"))
				.andExpect(jsonPath("$.error.message").value("혜택을 찾을 수 없습니다."));
	}

	@Test
	void 쿼리_파라미터_검증_실패는_필드_오류를_담은_400() throws Exception {
		mvc.perform(get("/api/test/validate").param("age", "200"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("age"));
	}

	@Test
	void 타입이_틀린_파라미터는_400() throws Exception {
		mvc.perform(get("/api/test/validate").param("age", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("age"));
	}

	@Test
	void 본문_검증_실패는_400() throws Exception {
		mvc.perform(post("/api/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
	}

	@Test
	void 읽을_수_없는_본문은_400() throws Exception {
		mvc.perform(post("/api/test/body").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

	@Test
	void 없는_주소는_404_공통형식() throws Exception {
		mvc.perform(get("/api/no-such-path"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void 예상하지_못한_예외는_500이고_내부_메시지를_숨긴다() throws Exception {
		mvc.perform(get("/api/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getMessage()));
	}

	@Test
	void 관리자_API는_로그인_없이_401() throws Exception {
		mvc.perform(get("/api/admin/test"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	@WithMockUser(roles = "USER")
	void 관리자_API는_일반_사용자에게_403() throws Exception {
		mvc.perform(get("/api/admin/test"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void 관리자_API는_ADMIN에게_열린다() throws Exception {
		mvc.perform(get("/api/admin/test"))
				.andExpect(status().isOk());
	}

	@Test
	void Swagger_명세가_공개된다() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk());
	}

	@Test
	void API_밖의_주소는_막힌다() throws Exception {
		mvc.perform(get("/internal"))
				.andExpect(status().isUnauthorized());
	}

	@TestConfiguration(proxyBeanMethods = false)
	@RestController
	static class TestApi {

		record Body(@NotBlank String name) {
		}

		@GetMapping("/api/test/ok")
		ApiResponse<Map<String, String>> ok() {
			return ApiResponse.ok(Map.of("hello", "world"));
		}

		@GetMapping("/api/test/not-found")
		ApiResponse<Void> notFound() {
			throw new BusinessException(ErrorCode.BENEFIT_NOT_FOUND);
		}

		@GetMapping("/api/test/validate")
		ApiResponse<Integer> validate(@RequestParam @Max(120) Integer age) {
			return ApiResponse.ok(age);
		}

		@PostMapping("/api/test/body")
		ApiResponse<String> body(@Valid @RequestBody Body body) {
			return ApiResponse.ok(body.name());
		}

		@GetMapping("/api/test/boom")
		ApiResponse<Void> boom() {
			throw new IllegalStateException("내부 정보");
		}

		@GetMapping("/api/admin/test")
		ApiResponse<String> admin() {
			return ApiResponse.ok("admin");
		}

	}

}
