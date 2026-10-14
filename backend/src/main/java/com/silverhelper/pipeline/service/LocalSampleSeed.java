package com.silverhelper.pipeline.service;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("local & !prod")
@Service
public class LocalSampleSeed {
    private final JdbcTemplate jdbc;

    public LocalSampleSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Result(int published, List<Long> benefitIds) {}

    @Transactional
    public Result publish(int limit) {
        List<Row> rows = jdbc.query("""
                select b.id, b.source, b.source_id from benefit b
                 where b.status='ACTIVE' and b.verified_hash is null
                   and exists(select 1 from benefit_raw r where r.benefit_id=b.id and r.kind='LIST')
                 order by case when b.name ~ '(노인|어르신|장기요양|치매|노년|수당)' then 0 else 1 end, b.id
                 limit ? for update of b skip locked
                """, (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)), limit);
        for (Row row : rows) {
            jdbc.update("update benefit set verified_hash=?, updated_at=now() where id=?",
                    DevSampleMarker.hash(row.source(), row.sourceId()), row.id());
        }
        return new Result(rows.size(), rows.stream().map(Row::id).toList());
    }

    private record Row(long id, String source, String sourceId) {}
}
