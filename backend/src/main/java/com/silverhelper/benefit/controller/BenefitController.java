package com.silverhelper.benefit.controller;

import com.silverhelper.benefit.dto.BenefitDtos.*;
import com.silverhelper.benefit.service.BenefitService;
import com.silverhelper.common.response.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
public class BenefitController {
    private final BenefitService service;
    public BenefitController(BenefitService service) { this.service = service; }

    @GetMapping("/api/benefits")
    public ApiResponse<PageResponse<Summary>> list(
            @RequestParam(required = false) @Min(0) @Max(120) Integer age,
            @RequestParam(required = false) String ctpvName,
            @RequestParam(required = false) String sggName,
            @RequestParam(required = false) @Min(1) @Max(6) Integer ltcGrade,
            @RequestParam(required = false) @Min(0) @Max(1000) Integer incomeMedianPct,
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "RELEVANCE") @Pattern(regexp = "RELEVANCE|NAME|RECENT") String sort) {
        return ApiResponse.ok(service.list(age,ctpvName,sggName,ltcGrade,incomeMedianPct,theme,keyword,page,size,sort));
    }
    @GetMapping("/api/benefits/{id}")
    public ApiResponse<Detail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @PostMapping("/api/benefits/match")
    public ApiResponse<MatchPage> match(@Valid @RequestBody Profile profile,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.match(profile,page,size));
    }
    @GetMapping("/api/codes")
    public ApiResponse<Map<String,List<Code>>> codes() { return ApiResponse.ok(service.codes()); }
    @GetMapping("/api/regions")
    public ApiResponse<List<RegionOption>> regions() { return ApiResponse.ok(service.regions()); }
}
