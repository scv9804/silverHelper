package com.silverhelper.pipeline.controller;

import com.silverhelper.common.response.ApiResponse;
import com.silverhelper.pipeline.service.LocalSampleSeed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("local & !prod")
@Validated
@RestController
@RequestMapping("/api/admin/dev")
public class LocalSampleSeedController {
    private final LocalSampleSeed seed;

    public LocalSampleSeedController(LocalSampleSeed seed) {
        this.seed = seed;
    }

    @PostMapping("/sample-seed")
    public ApiResponse<LocalSampleSeed.Result> publish(@RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return ApiResponse.ok(seed.publish(limit));
    }
}
