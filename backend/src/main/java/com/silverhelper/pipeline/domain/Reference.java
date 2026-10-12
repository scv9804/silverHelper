package com.silverhelper.pipeline.domain;

/** benefit_reference 1행. type은 CONTACT / SITE / LAW / FORM / APPLY */
public record Reference(String type, String name, String value) {
}
