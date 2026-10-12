package com.silverhelper.benefit.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "benefit_reference")
public class BenefitReference {
    @Id public Long id;
    @Column(name = "benefit_id") public Long benefitId;
    @Column(name = "ref_type") public String refType;
    public String name;
    public String value;
    @Column(name = "sort_order") public Integer sortOrder;
}
