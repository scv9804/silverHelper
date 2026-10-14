package com.silverhelper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.DevSampleMarker;
import com.silverhelper.pipeline.support.PipelineIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("local")
class LocalDemoTests extends PipelineIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 수집한_원문만_샘플로_공개하고_AI_검증과_구분한다() throws Exception {
        collectService.collect(Source.GOV24);
        mvc.perform(post("/api/admin/dev/sample-seed").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(1));
        var published = jdbc.queryForMap("select source, source_id, content_hash, verified_hash from benefit where verified_hash is not null");
        assertThat(published.get("verified_hash")).isEqualTo(
                DevSampleMarker.hash((String) published.get("source"), (String) published.get("source_id")));
        assertThat(published.get("verified_hash")).isNotEqualTo(published.get("content_hash"));
        assertThat(count("select count(*) from ai_extraction")).isZero();
        mvc.perform(get("/api/benefits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].summary").value(
                        org.hamcrest.Matchers.startsWith("개발용 샘플 데이터 · 자격 조건 미검증")));
        mvc.perform(post("/api/benefits/match").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":75,\"ctpvName\":\"서울특별시\",\"sggName\":\"종로구\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].matchLevel").value("CHECK_NEEDED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 일반_사용자는_샘플을_공개할_수_없다() throws Exception {
        mvc.perform(post("/api/admin/dev/sample-seed")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/dev/repair-stored-list")).andExpect(status().isForbidden());
    }

    @Test
    void 로컬_관리자_로그인은_관리자_토큰을_발급한다() throws Exception {
        mvc.perform(post("/api/dev/admin-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"local-demo-admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }
}
