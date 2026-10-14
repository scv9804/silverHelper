package com.silverhelper.notification.controller;

import com.silverhelper.common.exception.BusinessException;
import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import com.silverhelper.notification.dto.NotificationSettings;
import com.silverhelper.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notification-settings")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service=service; }
    @GetMapping public ApiResponse<NotificationSettings> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.get(userId(jwt)));
    }
    @PutMapping public ApiResponse<NotificationSettings> put(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationSettings settings) {
        return ApiResponse.ok(service.put(userId(jwt),settings));
    }
    private long userId(Jwt jwt) {
        if (jwt==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        try { return Long.parseLong(jwt.getSubject()); }
        catch (NumberFormatException e) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
    }
}
