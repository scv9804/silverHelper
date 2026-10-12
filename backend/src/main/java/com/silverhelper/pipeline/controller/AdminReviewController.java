package com.silverhelper.pipeline.controller;

import com.silverhelper.common.response.ApiResponse;
import com.silverhelper.common.response.PageResponse;
import com.silverhelper.pipeline.domain.ReviewReason;
import com.silverhelper.pipeline.domain.ReviewStatus;
import com.silverhelper.pipeline.dto.ApproveReviewRequest;
import com.silverhelper.pipeline.dto.RejectReviewRequest;
import com.silverhelper.pipeline.dto.ReviewDetailResponse;
import com.silverhelper.pipeline.dto.ReviewSummaryResponse;
import com.silverhelper.pipeline.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 검토 대기열 (openapi.yaml admin-reviews). 권한은 SecurityConfig의 /api/admin/** = ADMIN */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

	private final ReviewService reviewService;

	public AdminReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	/** 오래된 순. status 기본 PENDING */
	@GetMapping
	ApiResponse<PageResponse<ReviewSummaryResponse>> list(
			@RequestParam(required = false) ReviewStatus status,
			@RequestParam(required = false) ReviewReason reason,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ApiResponse.ok(reviewService.list(status, reason, page, size));
	}

	@GetMapping("/{id}")
	ApiResponse<ReviewDetailResponse> detail(@PathVariable long id) {
		return ApiResponse.ok(reviewService.detail(id));
	}

	@PostMapping("/{id}/approve")
	ApiResponse<ReviewSummaryResponse> approve(@PathVariable long id, @Valid @RequestBody ApproveReviewRequest request,
			Principal principal) {
		return ApiResponse.ok(reviewService.approve(id, request, actor(principal)));
	}

	@PostMapping("/{id}/reject")
	ApiResponse<ReviewSummaryResponse> reject(@PathVariable long id, @Valid @RequestBody RejectReviewRequest request,
			Principal principal) {
		return ApiResponse.ok(reviewService.reject(id, request, actor(principal)));
	}

	private static String actor(Principal principal) {
		return principal == null ? "unknown" : principal.getName();
	}

}
