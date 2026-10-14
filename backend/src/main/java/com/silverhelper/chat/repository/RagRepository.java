package com.silverhelper.chat.repository;

import com.silverhelper.chat.dto.ChatDtos;
import com.silverhelper.pipeline.domain.Source;
import com.silverhelper.pipeline.service.SourceMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RagRepository {
    public record Document(long id,String hash,String content) { }
    public record Hit(String content, ChatDtos.Source source, double distance) { }
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;
    private static final TypeReference<Map<String,Object>> PAYLOAD = new TypeReference<>() { };
    public RagRepository(NamedParameterJdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc; this.json=json; }

    public List<Document> missing(String model,int limit) {
        return jdbc.query("""
                select b.id,b.verified_hash,b.source,raw.payload::text payload
                  from benefit b
                  join benefit_raw raw on raw.benefit_id=b.id and raw.content_hash=b.verified_hash
                    and raw.kind=case when b.source='GOV24' then 'LIST' else 'DETAIL' end
                 where b.status='ACTIVE' and b.verified_hash is not null
                   and not exists (select 1 from rag_embedding r where r.benefit_id=b.id
                     and r.verified_hash=b.verified_hash and r.model=:model and r.chunk_no=-1)
                 order by b.id limit :limit
                """,Map.of("model",model,"limit",limit),
                (rs,n)->new Document(rs.getLong(1),rs.getString(2).trim(),
                        SourceMapper.sourceText(Source.valueOf(rs.getString(3)),
                                json.readValue(rs.getString(4),PAYLOAD)).joined()));
    }

    public void save(Document doc,String model,int chunkNo,String content,float[] vector) {
        jdbc.update("""
                insert into rag_embedding(benefit_id,chunk_no,content,verified_hash,model,embedding)
                values (:id,:chunk,:content,:hash,:model,cast(:vector as vector))
                on conflict(benefit_id,chunk_no,verified_hash,model) do nothing
                """,new MapSqlParameterSource().addValue("id",doc.id()).addValue("chunk",chunkNo).addValue("content",content)
                .addValue("hash",doc.hash()).addValue("model",model).addValue("vector",literal(vector)));
    }

    public List<Hit> search(String model,float[] vector) {
        return jdbc.query("""
                select r.content,b.id,b.name,b.source_url,b.last_collected_at,b.source_modified_at,b.base_year,
                       b.content_hash <> b.verified_hash update_pending,
                       r.embedding <=> cast(:vector as vector) distance
                  from rag_embedding r join benefit b on b.id=r.benefit_id
                 where r.model=:model and r.chunk_no>=0 and r.verified_hash=b.verified_hash
                   and b.status='ACTIVE' and b.verified_hash is not null
                 order by r.embedding <=> cast(:vector as vector) limit 8
                """,Map.of("model",model,"vector",literal(vector)),(rs,n)->new Hit(rs.getString("content"),
                new ChatDtos.Source(rs.getLong("id"),rs.getString("name"),rs.getString("source_url"),
                        new ChatDtos.Basis(rs.getObject("last_collected_at",OffsetDateTime.class),
                                rs.getObject("source_modified_at",OffsetDateTime.class),
                                rs.getObject("base_year",Short.class),rs.getBoolean("update_pending"))),
                rs.getDouble("distance")));
    }

    private static String literal(float[] vector) {
        StringBuilder out=new StringBuilder("[");
        for (int i=0;i<vector.length;i++) { if (i>0) out.append(','); out.append(vector[i]); }
        return out.append(']').toString();
    }
}
