package com.silverhelper.benefit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

@Entity
@Table(name = "benefit_eligibility")
public class BenefitEligibility {
    @Id @Column(name = "benefit_id") public Long benefitId;
    @Column(name = "extraction_id") public Long extractionId;
    @Column(name = "min_age") public Short minAge;
    @Column(name = "max_age") public Short maxAge;
    @Column(name = "ltc_required") public Boolean ltcRequired;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "ltc_grades", columnDefinition = "smallint[]") public Short[] ltcGrades;
    @Column(name = "income_max_median_pct") public Short incomeMaxMedianPct;
    @Column(name = "requires_basic_livelihood") public Boolean requiresBasicLivelihood;
    @Column(name = "requires_near_poverty") public Boolean requiresNearPoverty;
    @Column(name = "requires_basic_pension") public Boolean requiresBasicPension;
    @Column(name = "requires_disability") public Boolean requiresDisability;
    @Column(name = "requires_veteran") public Boolean requiresVeteran;
    @Column(name = "requires_single_household") public Boolean requiresSingleHousehold;
    @Column(name = "conditions_summary") public String conditionsSummary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "field_sources", columnDefinition = "jsonb") public Map<String, Object> fieldSources;
}
