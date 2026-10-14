package com.silverhelper.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationSettings(@NotNull Boolean emailEnabled, @NotNull Boolean newBenefitEnabled,
        @NotNull Boolean deadlineEnabled, @NotNull @Min(1) @Max(30) Integer deadlineDays) {
    public static NotificationSettings defaults() { return new NotificationSettings(false, true, true, 7); }
}
