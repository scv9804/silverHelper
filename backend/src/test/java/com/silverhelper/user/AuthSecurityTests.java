package com.silverhelper.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.silverhelper.common.response.ApiResponse;
import com.silverhelper.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers=AuthSecurityTests.TestApi.class)
@Import({AuthKeyConfig.class,AuthSecurity.class,SecurityConfig.class,AuthSecurityTests.TestApi.class})
class AuthSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthenticationSuccessHandler oauthSuccess;
    @RestController static class TestApi {
        @GetMapping("/api/benefits/test") ApiResponse<String> publicApi() {return ApiResponse.ok("open");}
        @GetMapping("/api/admin/test") ApiResponse<String> admin() {return ApiResponse.ok("admin");}
    }
    @Test void 공개_API는_익명으로_조회된다() throws Exception {
        mvc.perform(get("/api/benefits/test")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("open"));
    }
    @Test void 신규_체인_밖의_경로는_기존_체인이_처리한다() throws Exception {
        mvc.perform(get("/internal")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
    @Test void 관리자_API는_익명_401() throws Exception {
        mvc.perform(get("/api/admin/test")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
    @Test void 잘못된_토큰도_공통_401_형식이다() throws Exception {
        mvc.perform(get("/api/admin/test").header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
    @Test @WithMockUser(roles="USER") void 관리자_API는_일반_사용자_403() throws Exception {
        mvc.perform(get("/api/admin/test")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="ADMIN") void 관리자_API는_관리자에게_열린다() throws Exception {
        mvc.perform(get("/api/admin/test")).andExpect(status().isOk());
    }
}
