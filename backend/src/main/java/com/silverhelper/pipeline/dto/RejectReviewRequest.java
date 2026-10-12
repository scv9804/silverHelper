package com.silverhelper.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectReviewRequest(@NotBlank @Size(max = 1000) String note) {
}
