package com.silverhelper.pipeline.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 실제 AI 검증 해시와 구분되는 로컬 샘플 공개 표식. */
public final class DevSampleMarker {
    private DevSampleMarker() {}

    public static String hash(String source, String sourceId) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(("DEV_SAMPLE:" + source + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String source, String sourceId, String verifiedHash) {
        return verifiedHash != null && verifiedHash.trim().equals(hash(source, sourceId));
    }
}
