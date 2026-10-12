package com.silverhelper.benefit.domain;

import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "benefit")
public class Benefit {
    @Id public Long id;
    @Column(name = "group_id") public Long groupId;
    public String source;
    @Column(name = "source_id") public String sourceId;
    public String name;
    public String summary;
    @Column(name = "source_url") public String sourceUrl;
    @Column(name = "agency_name") public String agencyName;
    @Column(name = "ctpv_name") public String ctpvName;
    @Column(name = "sgg_name") public String sggName;
    @Column(name = "support_cycle") public String supportCycle;
    @Column(name = "provision_type") public String provisionType;
    @Column(name = "online_apply") public Boolean onlineApply;
    public String contact;
    @Column(name = "base_year") public Short baseYear;
    @Column(name = "enforce_start_date") public LocalDate enforceStartDate;
    @Column(name = "enforce_end_date") public LocalDate enforceEndDate;
    @Column(name = "source_modified_at") public OffsetDateTime sourceModifiedAt;
    @Column(name = "last_collected_at") public OffsetDateTime lastCollectedAt;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "content_hash", length = 64) public String contentHash;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "verified_hash", length = 64) public String verifiedHash;
    public String status;
    @Column(name = "target_content") public String targetContent;
    @Column(name = "criteria_content") public String criteriaContent;
    @Column(name = "benefit_content") public String benefitContent;
    @Column(name = "apply_content") public String applyContent;
}
