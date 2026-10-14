package com.silverhelper.pipeline.service;

import com.silverhelper.pipeline.client.SourceRecord;
import com.silverhelper.pipeline.domain.ListFields;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.domain.TagCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 저장된 최신 LIST 원문에서 분류·현재 시도명만 다시 계산한다. 수집·검증 이력은 수정하지 않는다. */
@Service
public class StoredListRepair {
    private static final TypeReference<Map<String, Object>> FIELDS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public StoredListRepair(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Report(int scanned, int tagChanged, int regionChanged, long nextAfterId, boolean done) {}
    private record Row(long id, Source source, String sourceId, String ctpv, String sgg, String payload) {}

    @Transactional
    public Report repairBatch(long afterId, int limit) {
        List<Row> rows = jdbc.query("""
                select b.id, b.source, b.source_id, b.ctpv_name, b.sgg_name, r.payload::text
                  from benefit b
                  join lateral (
                    select payload from benefit_raw
                     where benefit_id=b.id and kind='LIST'
                     order by last_seen_at desc, id desc limit 1
                  ) r on true
                 where b.id > ? order by b.id limit ?
                """, (rs, i) -> new Row(rs.getLong(1), Source.valueOf(rs.getString(2)), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6)), afterId, limit);
        int tagChanged = 0;
        int regionChanged = 0;
        for (Row row : rows) {
            Map<String, Object> fields = json.readValue(row.payload(), FIELDS);
            SourceRecord record = new SourceRecord(fields, "");
            if (!row.sourceId().equals(SourceMapper.sourceId(row.source(), record))) {
                throw new IllegalStateException("LIST 원문 서비스 ID 불일치: " + row.id());
            }
            ListFields mapped = SourceMapper.listFields(row.source(), record);
            Set<TagCode> targetTags = new HashSet<>(mapped.tags());
            Set<TagCode> currentTags = new HashSet<>(jdbc.query(
                    "select tag_type, code from benefit_tag where benefit_id=?",
                    (rs, i) -> new TagCode(rs.getString(1), rs.getString(2)), row.id()));
            if (!currentTags.equals(targetTags)) {
                jdbc.update("delete from benefit_tag where benefit_id=?", row.id());
                for (TagCode tag : targetTags) {
                    jdbc.update("insert into benefit_tag(benefit_id,tag_type,code) values(?,?,?)",
                            row.id(), tag.type(), tag.code());
                }
                tagChanged++;
            }
            if (!Objects.equals(row.ctpv(), mapped.ctpvName()) || !Objects.equals(row.sgg(), mapped.sggName())) {
                jdbc.update("update benefit set ctpv_name=?, sgg_name=? where id=?",
                        mapped.ctpvName(), mapped.sggName(), row.id());
                regionChanged++;
            }
        }
        long next = rows.isEmpty() ? afterId : rows.getLast().id();
        return new Report(rows.size(), tagChanged, regionChanged, next, rows.size() < limit);
    }
}
