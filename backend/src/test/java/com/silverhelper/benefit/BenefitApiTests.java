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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.ai.anthropic.api-key=test-key", "spring.ai.openai.api-key=test-key"})
@AutoConfigureMockMvc
@Transactional
class BenefitApiTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Test void 빈_DB에서도_기본_시도_목록을_반환한다() throws Exception {
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from benefit", Long.class)).isZero();
        mvc.perform(get("/api/regions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(17))
                .andExpect(jsonPath("$.data[0].ctpvName").value("서울특별시"))
                .andExpect(jsonPath("$.data[0].sggNames.length()").value(0));
    }
    @Test void 전체와_맞춤_목록의_size_상한은_100이다() throws Exception {
        String profile = "{\"age\":75,\"ctpvName\":\"서울특별시\"}";
        mvc.perform(get("/api/benefits").param("size", "100")).andExpect(status().isOk());
        mvc.perform(get("/api/benefits").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/benefits/match").contentType(MediaType.APPLICATION_JSON).content(profile)
                .param("size", "100")).andExpect(status().isOk());
        mvc.perform(post("/api/benefits/match").contentType(MediaType.APPLICATION_JSON).content(profile)
                .param("size", "101")).andExpect(status().isBadRequest());
    }
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
    private void eligibility(long id, int min, int max, int income, String grades, Boolean pension) {
        long raw = jdbc.queryForObject("""
                insert into benefit_raw(benefit_id,kind,content_hash,payload,raw_body,first_seen_at,last_seen_at)
                values (?,'DETAIL',repeat('a',64),'{}'::jsonb,'test',now(),now()) returning id
                """, Long.class, id);
        long extraction = jdbc.queryForObject("""
                insert into ai_extraction(benefit_id,raw_id,provider,model,prompt_version,schema_valid)
                values (?,?,'CLAUDE','test','v1',true) returning id
                """, Long.class, id, raw);
        jdbc.update("""
                insert into benefit_eligibility(benefit_id,extraction_id,min_age,max_age,income_max_median_pct,
                ltc_required,ltc_grades,requires_basic_pension,verified_at)
                values (?,?,?,?,?,true,?::smallint[],?,now())
                """, id, extraction, min, max, income, grades, pension);
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
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/benefits").param("ctpvName","서울특별시"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
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
    @Test void 목록_조건_정렬_페이징과_공개상태() throws Exception {
        long a = benefit(group(),"NATIONAL","가 돌봄",null,null,true);
        eligibility(a,65,80,50,"{3}",true);
        jdbc.update("insert into benefit_tag(benefit_id,tag_type,code) values (?,'THEME','120')",a);
        long b = benefit(group(),"LOCAL","나 일자리","서울특별시","강서구",true);
        jdbc.update("insert into benefit_tag(benefit_id,tag_type,code) values (?,'THEME','050')",b);
        jdbc.update("update benefit set source_modified_at=now(),content_hash=repeat('b',64) where id=?",b);
        long ended = benefit(group(),"NATIONAL","끝난 사업",null,null,true);
        jdbc.update("update benefit set status='ENDED' where id=?",ended);
        long removed = benefit(group(),"NATIONAL","제거 사업",null,null,true);
        jdbc.update("update benefit set status='REMOVED' where id=?",removed);
        mvc.perform(get("/api/benefits").param("age","65").param("ltcGrade","3").param("incomeMedianPct","50")
                .param("theme","050,120").param("keyword","가 돌봄"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(a));
        mvc.perform(get("/api/benefits").param("age","64").param("keyword","가 돌봄"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/benefits").param("age","80").param("keyword","가 돌봄"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/benefits").param("age","81").param("keyword","가 돌봄"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/benefits").param("ltcGrade","2").param("keyword","가 돌봄"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/benefits").param("incomeMedianPct","51").param("keyword","가 돌봄"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/benefits").param("theme","050,120").param("sort","NAME")
                .param("size","1").param("page","1"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(b));
        mvc.perform(get("/api/benefits").param("sort","RECENT"))
                .andExpect(jsonPath("$.data.content[0].id").value(b))
                .andExpect(jsonPath("$.data.content[0].basis.updatePending").value(true));
    }
    @Test void 매칭_조건_지역_정렬_페이징() throws Exception {
        long eligible = benefit(group(),"NATIONAL","나 가능",null,null,true);
        long unknown = benefit(group(),"LOCAL","가 확인","서울특별시","강서구",true);
        eligibility(unknown,70,90,50,"{3}",true);
        long wrongRegion = benefit(group(),"LOCAL","다 다른 지역","부산광역시",null,true);
        long tooOld = benefit(group(),"NATIONAL","라 연령 위반",null,null,true);
        eligibility(tooOld,10,20,50,"{3}",true);
        String body = "{\"age\":78,\"ctpvName\":\"서울특별시\",\"sggName\":\"강서구\"}";
        mvc.perform(post("/api/benefits/match").contentType(MediaType.APPLICATION_JSON).content(body)
                .param("size","1").param("page","0"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].benefit.id").value(eligible))
                .andExpect(jsonPath("$.data.summary.eligibleCount").value(1));
        mvc.perform(post("/api/benefits/match").contentType(MediaType.APPLICATION_JSON).content(body)
                .param("size","1").param("page","1"))
                .andExpect(jsonPath("$.data.content[0].benefit.id").value(unknown))
                .andExpect(jsonPath("$.data.content[0].matchLevel").value("CHECK_NEEDED"))
                .andExpect(jsonPath("$.data.content[0].checkItems[0]").exists());
    }
    @Test void 수십_건의_페이지_조회도_쿼리_수가_일정하다() throws Exception {
        for (int i=0;i<40;i++) benefit(group(),"NATIONAL","사업 "+i,null,null,true);
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().setStatisticsEnabled(true);
        factory.getStatistics().clear();
        mvc.perform(get("/api/benefits").param("size","20"))
                .andExpect(jsonPath("$.data.totalElements").value(40))
                .andExpect(jsonPath("$.data.content.length()").value(20));
        org.assertj.core.api.Assertions.assertThat(factory.getStatistics().getQueryExecutionCount()).isLessThanOrEqualTo(3);
    }
}
