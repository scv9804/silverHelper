package com.silverhelper.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DevLoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class DevLoginWithoutProfileTests {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @MockitoBean UserStore users;
    @MockitoBean AuthService auth;

    @Test void 무프로필에서_개발용_로그인이_비활성이다() throws Exception {
        assertThat(environment.getActiveProfiles()).isEmpty();
        mvc.perform(post("/api/dev/login").contentType("application/json").content("{\"name\":\"tester\"}"))
                .andExpect(status().isNotFound());
    }
}
