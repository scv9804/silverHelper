package com.silverhelper.chat.controller;

import com.silverhelper.chat.dto.ChatDtos;
import com.silverhelper.chat.service.ChatService;
import com.silverhelper.chat.service.ChatRateLimiter;
import com.silverhelper.chat.service.ChatRateLimitedException;
import com.silverhelper.common.exception.BusinessException;
import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    private final ChatRateLimiter limiter;
    public ChatController(ChatService service, ChatRateLimiter limiter) { this.service=service; this.limiter=limiter; }
    @PostMapping public ApiResponse<ChatDtos.Answer> ask(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatDtos.Question request) {
        if (jwt==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        limiter.check(jwt.getSubject());
        return ApiResponse.ok(service.ask(request.question()));
    }

    @ExceptionHandler(ChatRateLimitedException.class)
    public ResponseEntity<ApiResponse<Void>> rateLimited(ChatRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(e.retryAfterSeconds()))
                .body(ApiResponse.fail(ErrorCode.CHAT_RATE_LIMITED));
    }
}
