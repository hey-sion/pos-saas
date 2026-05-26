package com.sion.pos.support.portone;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * PortOne V2 웹훅 서명 검증. Standard Webhooks 스펙(HMAC-SHA256, 대칭키)을 따른다.
 * 서명 대상 문자열은 {@code {msgId}.{timestamp}.{body}} 이며, 시크릿은 whsec_ 접두사를 떼고 base64 디코딩해 키로 쓴다.
 */
@Component
public class PortOneWebhookVerifier {

    private static final String SECRET_PREFIX = "whsec_";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SUPPORTED_VERSION = "v1";
    private static final long TOLERANCE_SECONDS = 5 * 60;

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public PortOneWebhookVerifier(PortOneProperties properties) {
        this(properties.webhookSecret(), Clock.systemUTC());
    }

    PortOneWebhookVerifier(String secret, Clock clock) {
        String base64 = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        this.key = Base64.getDecoder().decode(base64);
        this.clock = clock;
    }

    public void verify(String body, String msgId, String signatureHeader, String timestampHeader) {
        if (body == null || msgId == null || signatureHeader == null || timestampHeader == null) {
            throw new WebhookVerificationException("필수 헤더 또는 본문이 누락되었습니다.");
        }

        verifyTimestampWithinTolerance(timestampHeader);

        byte[] expected = sign(msgId, timestampHeader, body);
        boolean matched = Arrays.stream(signatureHeader.split(" "))
                                .anyMatch(versionedSignature -> matches(versionedSignature, expected));
        if (!matched) {
            throw new WebhookVerificationException("일치하는 서명이 없습니다.");
        }
    }

    private boolean matches(String versionedSignature, byte[] expected) {
        String[] parts = versionedSignature.split(",", 2);
        if (parts.length < 2 || !SUPPORTED_VERSION.equals(parts[0])) {
            return false;
        }
        try {
            byte[] provided = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(provided, expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(String msgId, String timestampHeader, String body) {
        String content = msgId + "." + timestampHeader + "." + body;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC 서명 계산에 실패했습니다.", e);
        }
    }

    private void verifyTimestampWithinTolerance(String timestampHeader) {
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new WebhookVerificationException("timestamp 형식이 올바르지 않습니다.");
        }

        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > TOLERANCE_SECONDS) {
            throw new WebhookVerificationException("timestamp가 허용 범위를 벗어났습니다.");
        }
    }
}