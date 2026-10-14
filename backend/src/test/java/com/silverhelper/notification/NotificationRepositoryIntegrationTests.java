package com.silverhelper.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.silverhelper.TestcontainersConfiguration;
import com.silverhelper.notification.repository.NotificationRepository;
import com.silverhelper.notification.repository.NotificationRepository.Candidate;
import java.time.LocalDate;
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
class NotificationRepositoryIntegrationTests {
    private static final LocalDate TODAY=LocalDate.of(2026,10,14);
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository repository;

    @Test
    void 같은_배송_키의_PENDING과_SENT는_중복_예약하지_않고_FAILED만_재예약한다() {
        long user=userWithProfile();
        benefit("서울특별시","강남구",false);
        Candidate candidate=repository.candidates(TODAY).stream().filter(c -> c.userId()==user).findFirst().orElseThrow();

        Long first=repository.reserve(candidate,"NEW_BENEFIT","2026-10-10");
        assertThat(first).isNotNull();
        assertThat(repository.reserve(candidate,"NEW_BENEFIT","2026-10-10")).isNull();
        repository.sent(first);
        assertThat(repository.reserve(candidate,"NEW_BENEFIT","2026-10-10")).isNull();
        jdbc.update("update notification_delivery set status='FAILED' where id=?",first);
        assertThat(repository.reserve(candidate,"NEW_BENEFIT","2026-10-10")).isEqualTo(first);
        assertThat(jdbc.queryForObject("select status from notification_delivery where id=?",String.class,first))
                .isEqualTo("PENDING");
    }

    @Test
    void 후보_조회는_실제_스키마와_지역_조건을_따르고_검증일을_기준일로_쓴다() {
        long user=userWithProfile();
        long verified=benefit("서울특별시","강남구",true);
        long fallback=benefit("서울특별시",null,false);
        benefit("서울특별시","송파구",false);
        benefit("부산광역시",null,false);

        List<Candidate> candidates=repository.candidates(TODAY).stream().filter(c -> c.userId()==user).toList();

        assertThat(candidates).extracting(Candidate::benefitId).containsExactlyInAnyOrder(verified,fallback);
        assertThat(candidates.stream().filter(c -> c.benefitId()==verified).findFirst().orElseThrow().basisDate())
                .isEqualTo(LocalDate.of(2026,10,11));
        assertThat(candidates.stream().filter(c -> c.benefitId()==fallback).findFirst().orElseThrow().basisDate())
                .isEqualTo(LocalDate.of(2026,10,13));
    }

    private long userWithProfile() {
        long user=jdbc.queryForObject("insert into user_account(email,display_name) values ('parent@example.org','보호자') returning id",Long.class);
        jdbc.update("insert into parent_profile(user_id,name,age,ctpv_name,sgg_name) values (?,'부모',75,'서울특별시','강남구')",user);
        jdbc.update("insert into notification_setting(user_id,email_enabled) values (?,true)",user);
        return user;
    }

    private long benefit(String ctpv,String sgg,boolean verifiedAt) {
        long group=jdbc.queryForObject("insert into benefit_group default values returning id",Long.class);
        long benefit=jdbc.queryForObject("""
                insert into benefit(source,source_id,group_id,name,source_url,ctpv_name,sgg_name,
                                    content_hash,verified_hash,status,enforce_end_date,first_collected_at,last_collected_at)
                values ('NATIONAL',gen_random_uuid()::text,?,'테스트 혜택','https://example.org',?,?,'hash','hash',
                        'ACTIVE','2026-10-17','2026-10-10T01:00:00+09:00','2026-10-13T12:00:00+09:00') returning id
                """,Long.class,group,ctpv,sgg);
        if (verifiedAt) {
            long raw=jdbc.queryForObject("""
                    insert into benefit_raw(benefit_id,kind,content_hash,payload,raw_body,first_seen_at,last_seen_at)
                    values (?,'DETAIL','hash','{}'::jsonb,'{}',now(),now()) returning id
                    """,Long.class,benefit);
            long extraction=jdbc.queryForObject("""
                    insert into ai_extraction(benefit_id,raw_id,provider,model,prompt_version,schema_valid)
                    values (?,?,'CLAUDE','test','test-v1',true) returning id
                    """,Long.class,benefit,raw);
            jdbc.update("insert into benefit_eligibility(benefit_id,extraction_id,verified_at) values (?,?,'2026-10-11T23:30:00+09:00')",
                    benefit,extraction);
        }
        return benefit;
    }
}
