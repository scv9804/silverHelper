package com.silverhelper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.StoredListRepair;
import com.silverhelper.pipeline.support.PipelineIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoredListRepairTests extends PipelineIntegrationTest {
    @Autowired StoredListRepair repair;

    @Test
    void 최신_LIST만_재해석해_태그와_지역만_멱등_보정한다() {
        collectService.collect(Source.LOCAL);
        long id = benefitId(Source.LOCAL, "WLF00006781");
        jdbc.update("update benefit_raw set payload=jsonb_set(jsonb_set(payload,'{ctpvNm}','\"광주광역시\"'::jsonb),'{sggNm}','\"북구\"'::jsonb) where benefit_id=? and kind='LIST'", id);
        jdbc.update("insert into benefit_tag(benefit_id,tag_type,code) values (?,'THEME','130')", id);
        jdbc.update("update benefit set ctpv_name='지역 확인 필요',sgg_name=null where id=?", id);
        jdbc.update("""
                insert into benefit_raw(benefit_id,kind,content_hash,payload,raw_body,first_seen_at,last_seen_at)
                select benefit_id,'LIST',repeat('f',64),
                       jsonb_set(payload,'{ctpvNm}','"부산광역시"'::jsonb),'old',
                       '2000-01-01'::timestamptz,'2000-01-01'::timestamptz
                  from benefit_raw where benefit_id=? and kind='LIST' limit 1
                """, id);
        Map<String, Object> untouched = jdbc.queryForMap("""
                select content_hash,verified_hash,status,first_collected_at,last_collected_at,created_at,updated_at
                  from benefit where id=?
                """, id);
        var rawUntouched = jdbc.queryForList("""
                select id,content_hash,payload::text,raw_body,first_seen_at,last_seen_at
                  from benefit_raw where benefit_id=? order by id
                """, id);
        int rawCount = count("select count(*) from benefit_raw where benefit_id=?", id);

        StoredListRepair.Report first = repair.repairBatch(0, 500);
        assertThat(first.scanned()).isEqualTo(3);
        assertThat(first.tagChanged()).isEqualTo(1);
        assertThat(first.regionChanged()).isEqualTo(1);
        assertThat(first.done()).isTrue();
        assertThat(jdbc.queryForObject("select ctpv_name from benefit where id=?", String.class, id))
                .isEqualTo("전남광주통합특별시");
        assertThat(jdbc.queryForObject("select sgg_name from benefit where id=?", String.class, id))
                .isEqualTo("북구");
        assertThat(count("select count(*) from benefit_tag where benefit_id=? and tag_type='THEME' and code='130'", id))
                .isZero();
        assertThat(jdbc.queryForMap("""
                select content_hash,verified_hash,status,first_collected_at,last_collected_at,created_at,updated_at
                  from benefit where id=?
                """, id)).isEqualTo(untouched);
        assertThat(count("select count(*) from benefit_raw where benefit_id=?", id)).isEqualTo(rawCount);
        assertThat(jdbc.queryForList("""
                select id,content_hash,payload::text,raw_body,first_seen_at,last_seen_at
                  from benefit_raw where benefit_id=? order by id
                """, id)).isEqualTo(rawUntouched);

        StoredListRepair.Report second = repair.repairBatch(0, 500);
        assertThat(second.tagChanged()).isZero();
        assertThat(second.regionChanged()).isZero();
    }
}
