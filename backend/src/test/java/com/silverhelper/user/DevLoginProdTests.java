package com.silverhelper.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DevLoginController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("prod")
@TestPropertySource(properties = "app.auth.jwt-secret=0123456789abcdef0123456789abcdef")
class DevLoginProdTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserStore users;
    @MockitoBean AuthService auth;

    @Test void 운영_프로필에서_개발용_로그인은_404다() throws Exception {
        mvc.perform(post("/api/dev/login").contentType("application/json").content("{\"name\":\"tester\"}"))
                .andExpect(status().isNotFound());
    }
}
