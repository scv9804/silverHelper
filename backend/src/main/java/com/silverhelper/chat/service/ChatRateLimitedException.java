package com.silverhelper.chat.service;

public class ChatRateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public ChatRateLimitedException(long retryAfterSeconds) {
        super("채팅 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }
}
