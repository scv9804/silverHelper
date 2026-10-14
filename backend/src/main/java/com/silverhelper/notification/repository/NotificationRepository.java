package com.silverhelper.notification.repository;

import com.silverhelper.notification.dto.NotificationSettings;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    public record Candidate(long userId, long profileId, long benefitId, String email, String profileName,
            String benefitName, String sourceUrl, String benefitCtpv, String benefitSgg,
            LocalDate firstSeen, LocalDate verifiedDate, LocalDate basisDate, LocalDate deadline, int days,
            boolean newEnabled, boolean deadlineEnabled, int age, String ctpv, String sgg,
            Integer ltcGrade, Boolean hasNoLtcGrade, Integer income, Boolean livelihood, Boolean nearPoverty,
            Boolean pension, Boolean disability, Boolean veteran, Boolean livingAlone,
            Integer minAge, Integer maxAge, Boolean ltcRequired, Short[] ltcGrades, Integer incomeLimit,
            Boolean requiresLivelihood, Boolean requiresNearPoverty, Boolean requiresPension,
            Boolean requiresDisability, Boolean requiresVeteran, Boolean requiresSingle) { }

    private final NamedParameterJdbcTemplate jdbc;
    public NotificationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public NotificationSettings settings(long userId) {
        return jdbc.query("select email_enabled, new_benefit_enabled, deadline_enabled, deadline_days from notification_setting where user_id=:id",
                Map.of("id", userId), (rs, n) -> new NotificationSettings(rs.getBoolean(1), rs.getBoolean(2),
                        rs.getBoolean(3), rs.getInt(4))).stream().findFirst().orElse(NotificationSettings.defaults());
    }

    public NotificationSettings save(long userId, NotificationSettings value) {
        jdbc.update("""
                insert into notification_setting(user_id,email_enabled,new_benefit_enabled,deadline_enabled,deadline_days)
                values (:id,:email,:newBenefit,:deadline,:days)
                on conflict(user_id) do update set email_enabled=excluded.email_enabled,
                  new_benefit_enabled=excluded.new_benefit_enabled, deadline_enabled=excluded.deadline_enabled,
                  deadline_days=excluded.deadline_days, updated_at=now()
                """, new MapSqlParameterSource().addValue("id", userId).addValue("email", value.emailEnabled())
                .addValue("newBenefit", value.newBenefitEnabled()).addValue("deadline", value.deadlineEnabled())
                .addValue("days", value.deadlineDays()));
        return value;
    }

    public List<Candidate> candidates(LocalDate today) {
        return jdbc.query("""
                select s.user_id, p.id profile_id, b.id benefit_id, u.email, p.name profile_name,
                       b.name benefit_name, b.source_url, b.ctpv_name benefit_ctpv, b.sgg_name benefit_sgg,
                       (b.first_collected_at at time zone 'Asia/Seoul')::date first_seen,
                       (e.verified_at at time zone 'Asia/Seoul')::date verified_date,
                       (coalesce(e.verified_at,b.last_collected_at) at time zone 'Asia/Seoul')::date basis_date,
                       b.enforce_end_date, s.deadline_days, s.new_benefit_enabled, s.deadline_enabled,
                       p.age, p.ctpv_name, p.sgg_name, p.ltc_grade, p.has_no_ltc_grade,
                       p.income_median_pct, p.basic_livelihood, p.near_poverty, p.basic_pension,
                       p.disability, p.veteran, p.living_alone, e.min_age, e.max_age, e.ltc_required, e.ltc_grades,
                       e.income_max_median_pct, e.requires_basic_livelihood, e.requires_near_poverty,
                       e.requires_basic_pension, e.requires_disability, e.requires_veteran, e.requires_single_household
                  from notification_setting s join user_account u on u.id=s.user_id
                  join parent_profile p on p.user_id=s.user_id
                  join benefit b on b.status='ACTIVE' and b.verified_hash is not null
                  left join benefit_eligibility e on e.benefit_id=b.id
                 where s.email_enabled and u.email is not null and u.email <> ''
                   and ((s.new_benefit_enabled and ((b.first_collected_at at time zone 'Asia/Seoul')::date=:today
                         or (e.verified_at at time zone 'Asia/Seoul')::date=:today)
                     or s.deadline_enabled and b.enforce_end_date between :today and :today + s.deadline_days)
                     or exists (
                     select 1 from notification_delivery d where d.user_id=s.user_id and d.profile_id=p.id
                       and d.benefit_id=b.id and d.status='FAILED'))
                   and (b.ctpv_name is null or b.ctpv_name=p.ctpv_name)
                   and (b.sgg_name is null or p.sgg_name is null or b.sgg_name=p.sgg_name)
                """, Map.of("today", today), (rs, n) -> new Candidate(
                rs.getLong("user_id"), rs.getLong("profile_id"), rs.getLong("benefit_id"), rs.getString("email"),
                rs.getString("profile_name"), rs.getString("benefit_name"), rs.getString("source_url"),
                rs.getString("benefit_ctpv"),rs.getString("benefit_sgg"),
                rs.getObject("first_seen", LocalDate.class), rs.getObject("verified_date", LocalDate.class),
                rs.getObject("basis_date", LocalDate.class),
                rs.getObject("enforce_end_date", LocalDate.class),
                rs.getInt("deadline_days"), rs.getBoolean("new_benefit_enabled"), rs.getBoolean("deadline_enabled"),
                rs.getInt("age"), rs.getString("ctpv_name"), rs.getString("sgg_name"), integer(rs,"ltc_grade"),
                bool(rs,"has_no_ltc_grade"), integer(rs,"income_median_pct"), bool(rs,"basic_livelihood"),
                bool(rs,"near_poverty"), bool(rs,"basic_pension"), bool(rs,"disability"), bool(rs,"veteran"),
                bool(rs,"living_alone"), integer(rs,"min_age"), integer(rs,"max_age"), bool(rs,"ltc_required"),
                rs.getArray("ltc_grades") == null ? null : (Short[]) rs.getArray("ltc_grades").getArray(),
                integer(rs,"income_max_median_pct"), bool(rs,"requires_basic_livelihood"),
                bool(rs,"requires_near_poverty"), bool(rs,"requires_basic_pension"), bool(rs,"requires_disability"),
                bool(rs,"requires_veteran"), bool(rs,"requires_single_household")));
    }

    private static Integer integer(java.sql.ResultSet rs, String key) throws java.sql.SQLException {
        int value=rs.getInt(key); return rs.wasNull()?null:value;
    }
    private static Boolean bool(java.sql.ResultSet rs, String key) throws java.sql.SQLException {
        boolean value=rs.getBoolean(key); return rs.wasNull()?null:value;
    }

    public Long reserve(Candidate c, String kind, String eventKey) {
        return jdbc.query("""
                insert into notification_delivery(user_id,profile_id,benefit_id,kind,event_key,status)
                values (:user,:profile,:benefit,:kind,:event,'PENDING')
                on conflict(user_id,profile_id,benefit_id,kind,event_key) do update
                  set status='PENDING', error_message=null, created_at=now()
                  where notification_delivery.status='FAILED'
                returning id
                """, Map.of("user",c.userId(),"profile",c.profileId(),"benefit",c.benefitId(),"kind",kind,"event",eventKey),
                (rs,n)->rs.getLong(1)).stream().findFirst().orElse(null);
    }

    public void sent(long id) {
        if (jdbc.update("update notification_delivery set status='SENT', sent_at=now(), error_message=null where id=:id and status='PENDING'", Map.of("id",id)) != 1)
            throw new IllegalStateException("PENDING delivery not found: " + id);
    }
    public void recordUncertain(long id, String error) {
        jdbc.update("update notification_delivery set error_message=:error where id=:id and status='PENDING'",
                Map.of("id",id,"error",error.substring(0,Math.min(error.length(),1000))));
    }
    public void failed(long id, String error) { jdbc.update("update notification_delivery set status='FAILED', error_message=:error where id=:id",
            Map.of("id",id,"error",error.substring(0,Math.min(error.length(),1000)))); }

    public boolean retryable(Candidate c,String kind,String event) {
        Integer count=jdbc.queryForObject("""
                select count(*) from notification_delivery where user_id=:user and profile_id=:profile
                  and benefit_id=:benefit and kind=:kind and event_key=:event
                  and status='FAILED'
                """,Map.of("user",c.userId(),"profile",c.profileId(),"benefit",c.benefitId(),"kind",kind,"event",event),Integer.class);
        return count!=null && count>0;
    }
}
