package com.silverhelper.benefit.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "benefit_tag")
@IdClass(BenefitTag.Key.class)
public class BenefitTag {
    @Id @Column(name = "benefit_id") public Long benefitId;
    @Id @Column(name = "tag_type") public String tagType;
    @Id public String code;

    public static class Key implements Serializable {
        public Long benefitId;
        public String tagType;
        public String code;
        public Key() {}
        public boolean equals(Object o) {
            return o instanceof Key k && java.util.Objects.equals(benefitId, k.benefitId)
                    && java.util.Objects.equals(tagType, k.tagType) && java.util.Objects.equals(code, k.code);
        }
        public int hashCode() { return java.util.Objects.hash(benefitId, tagType, code); }
    }
}
