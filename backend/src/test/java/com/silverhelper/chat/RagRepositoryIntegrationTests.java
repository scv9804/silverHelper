package com.silverhelper.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.TestcontainersConfiguration;
import com.silverhelper.chat.repository.RagRepository;
import com.silverhelper.chat.repository.RagRepository.Document;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.ai.anthropic.api-key=test-key", "spring.ai.openai.api-key=test-key",
        "pipeline.ai.real-enabled=false"})
@Transactional
class RagRepositoryIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired RagRepository repository;

    @Test
    void 공개_검색은_활성_검증_임베딩만_반환하고_검증된_이전_버전은_유지한다() {
        long active=benefit("ACTIVE","hash-a","hash-a");
        long removed=benefit("REMOVED","hash-b","hash-b");
        long unverified=benefit("ACTIVE","hash-c",null);
        long stale=benefit("ACTIVE","hash-new","hash-current");
        long pending=benefit("ACTIVE","hash-newer","hash-verified");
        float[] vector=new float[1536];
        vector[0]=1;
        repository.save(new Document(active,"hash-a",""),"integration",0,"활성 근거",vector);
        repository.save(new Document(removed,"hash-b",""),"integration",0,"삭제된 근거",vector);
        repository.save(new Document(unverified,"hash-c",""),"integration",0,"미검증 근거",vector);
        repository.save(new Document(stale,"hash-old",""),"integration",0,"이전 해시 근거",vector);
        repository.save(new Document(pending,"hash-verified",""),"integration",0,"검증된 과거 근거",vector);

        var hits=repository.search("integration",vector);

        assertThat(hits).extracting(hit -> hit.source().benefitId()).containsExactlyInAnyOrder(active,pending);
        assertThat(hits.stream().filter(hit -> hit.source().benefitId()==pending).findFirst().orElseThrow()
                .source().basis().updatePending()).isTrue();
    }

    private long benefit(String status,String contentHash,String verifiedHash) {
        long group=jdbc.queryForObject("insert into benefit_group default values returning id",Long.class);
        return jdbc.queryForObject("""
                insert into benefit(source,source_id,group_id,name,source_url,content_hash,verified_hash,status,
                                    first_collected_at,last_collected_at)
                values ('NATIONAL',gen_random_uuid()::text,?,'테스트 혜택','https://example.org',?,?,?,now(),now()) returning id
                """,Long.class,group,contentHash,verifiedHash,status);
    }
}
