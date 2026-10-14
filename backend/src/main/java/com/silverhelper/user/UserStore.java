package com.silverhelper.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserStore {
    private final JdbcTemplate jdbc;

    public UserStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record User(long id, String displayName, String email, String role) {}
    public record Profile(long id, String name, Integer age, String ctpvName, String sggName,
            Integer ltcGrade, Boolean hasNoLtcGrade, Integer incomeMedianPct, Boolean basicLivelihood,
            Boolean nearPoverty, Boolean basicPension, Boolean disability, Boolean veteran, Boolean livingAlone) {}

    private static User user(ResultSet rs, int row) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("display_name"), rs.getString("email"), rs.getString("role"));
    }
    private static Profile profile(ResultSet rs, int row) throws SQLException {
        return new Profile(rs.getLong("id"), rs.getString("name"), rs.getObject("age",Integer.class),
                rs.getString("ctpv_name"), rs.getString("sgg_name"), rs.getObject("ltc_grade",Integer.class),
                (Boolean) rs.getObject("has_no_ltc_grade"), rs.getObject("income_median_pct",Integer.class),
                (Boolean) rs.getObject("basic_livelihood"), (Boolean) rs.getObject("near_poverty"),
                (Boolean) rs.getObject("basic_pension"), (Boolean) rs.getObject("disability"),
                (Boolean) rs.getObject("veteran"), (Boolean) rs.getObject("living_alone"));
    }
    public Optional<User> find(long id) {
        return jdbc.query("select id, display_name, email, role from user_account where id=?", UserStore::user, id).stream().findFirst();
    }
    public boolean isDev(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from social_account where user_id=? and provider='DEV')",Boolean.class,id));
    }
    @Transactional
    public User social(String provider, String providerId, String name, String email) {
        // 같은 소셜 식별자의 첫 로그인만 직렬화한다. 트랜잭션 종료까지 잠금이 유지된다.
        jdbc.query("select pg_advisory_xact_lock(hashtext(?), hashtext(?))", (rs, row) -> 1,
                provider, providerId);
        List<User> existing = jdbc.query("select u.id,u.display_name,u.email,u.role from user_account u join social_account s on s.user_id=u.id where s.provider=? and s.provider_user_id=?", UserStore::user, provider, providerId);
        if (!existing.isEmpty()) return existing.getFirst();
        Long id = jdbc.queryForObject("insert into user_account(display_name,email,role) values(?,?,'USER') returning id", Long.class, name, email);
        jdbc.update("insert into social_account(user_id,provider,provider_user_id) values(?,?,?)", id, provider, providerId);
        return find(id).orElseThrow();
    }
    public List<Profile> profiles(long userId) {
        return jdbc.query("select * from parent_profile where user_id=? order by id", UserStore::profile, userId);
    }
    public Optional<Profile> profile(long userId, long id) {
        return jdbc.query("select * from parent_profile where user_id=? and id=?", UserStore::profile, userId, id).stream().findFirst();
    }
    public Profile create(long userId, Profile p) {
        Long id = jdbc.queryForObject("insert into parent_profile(user_id,name,age,ctpv_name,sgg_name,ltc_grade,has_no_ltc_grade,income_median_pct,basic_livelihood,near_poverty,basic_pension,disability,veteran,living_alone) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id", Long.class,
                userId,p.name(),p.age(),p.ctpvName(),p.sggName(),p.ltcGrade(),p.hasNoLtcGrade(),p.incomeMedianPct(),p.basicLivelihood(),p.nearPoverty(),p.basicPension(),p.disability(),p.veteran(),p.livingAlone());
        return profile(userId,id).orElseThrow();
    }
    public boolean update(long userId, long id, Profile p) {
        return jdbc.update("update parent_profile set name=?,age=?,ctpv_name=?,sgg_name=?,ltc_grade=?,has_no_ltc_grade=?,income_median_pct=?,basic_livelihood=?,near_poverty=?,basic_pension=?,disability=?,veteran=?,living_alone=?,updated_at=now() where user_id=? and id=?",
                p.name(),p.age(),p.ctpvName(),p.sggName(),p.ltcGrade(),p.hasNoLtcGrade(),p.incomeMedianPct(),p.basicLivelihood(),p.nearPoverty(),p.basicPension(),p.disability(),p.veteran(),p.livingAlone(),userId,id)>0;
    }
    public boolean delete(long userId, long id) {
        return jdbc.update("delete from parent_profile where user_id=? and id=?",userId,id)>0;
    }
    public List<Long> saved(long userId) {
        return jdbc.queryForList("select benefit_id from saved_benefit where user_id=? order by created_at,benefit_id", Long.class, userId);
    }
    public boolean benefitExists(long benefitId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from benefit where id=?)",Boolean.class,benefitId));
    }
    public void save(long userId,long benefitId) {
        jdbc.update("insert into saved_benefit(user_id,benefit_id) values(?,?) on conflict do nothing",userId,benefitId);
    }
    public void unsave(long userId,long benefitId) {
        jdbc.update("delete from saved_benefit where user_id=? and benefit_id=?",userId,benefitId);
    }
}
