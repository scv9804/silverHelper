package com.silverhelper.chat.controller;

import com.silverhelper.chat.dto.ChatDtos;
import com.silverhelper.chat.service.ChatService;
import com.silverhelper.common.exception.BusinessException;
import com.silverhelper.common.exception.ErrorCode;
import com.silverhelper.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service=service; }
    @PostMapping public ApiResponse<ChatDtos.Answer> ask(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatDtos.Question request) {
        if (jwt==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.ok(service.ask(request.question()));
    }
}
