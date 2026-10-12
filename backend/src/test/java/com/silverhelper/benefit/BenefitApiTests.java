package com.silverhelper.benefit;

import com.silverhelper.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.ai.anthropic.api-key=test-key", "spring.ai.openai.api-key=test-key"})
@AutoConfigureMockMvc
@Transactional
class BenefitApiTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private long group() { return jdbc.queryForObject("insert into benefit_group default values returning id", Long.class); }
    private long benefit(long group, String source, String name, String ctpv, String sgg, boolean visible) {
        long id = jdbc.queryForObject("""
                insert into benefit (source,source_id,group_id,name,summary,source_url,ctpv_name,sgg_name,
                content_hash,status,first_collected_at,last_collected_at)
                values (?,?,?,?,?,?,?,?,repeat('a',64),'ACTIVE',now(),now()) returning id
                """, Long.class, source, UUID.randomUUID().toString(), group, name, "돌봄 지원", "https://example.org/"+name, ctpv, sgg);
        if (visible) jdbc.update("update benefit set verified_hash=content_hash where id=?", id);
        return id;
    }
    @Test void 목록_대표_필터_비공개_상세_매칭() throws Exception {
        long g = group();
        long national = benefit(g,"NATIONAL","돌봄 전국",null,null,true);
        benefit(g,"LOCAL","돌봄 중복","서울특별시","강서구",true);
        jdbc.update("update benefit_group set primary_benefit_id=? where id=?",national,g);
        long local = benefit(group(),"LOCAL","서울 돌봄","서울특별시","강서구",true);
        benefit(group(),"GOV24","미검증",null,null,false);
        jdbc.update("insert into benefit_tag(benefit_id,tag_type,code) values (?,'THEME','120')",local);
        mvc.perform(get("/api/benefits")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/benefits").param("ctpvName","서울특별시").param("sggName","강서구")
                .param("theme","120")).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(local));
        mvc.perform(get("/api/benefits/"+national)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otherSources[0].source").value("LOCAL"));
        mvc.perform(get("/api/benefits/99999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BENEFIT_NOT_FOUND"));
        mvc.perform(post("/api/benefits/match").contentType(MediaType.APPLICATION_JSON)
                .content("{\"age\":78,\"ctpvName\":\"서울특별시\",\"sggName\":\"강서구\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.summary.eligibleCount").value(2));
        mvc.perform(get("/api/regions")).andExpect(jsonPath("$.data[0].ctpvName").value("서울특별시"));
        mvc.perform(get("/api/codes")).andExpect(jsonPath("$.data.THEME[?(@.code=='120')].name").exists());
    }
    @Test void 잘못된_파라미터는_400() throws Exception {
        mvc.perform(get("/api/benefits").param("age","200")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/benefits").param("ltcGrade","7")).andExpect(status().isBadRequest());
    }
}
