package com.silverhelper.benefit.domain;

import jakarta.persistence.*;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "benefit_group")
public class BenefitGroup {
    @Id public Long id;
    @Column(name = "primary_benefit_id") public Long primaryBenefitId;
}
