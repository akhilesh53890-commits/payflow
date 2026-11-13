package com.payflow.webhook.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class WebhookSignatureService {

    public String computeSignatureHeader(String payload, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        String toSign = timestamp + "." + payload;
        String signature = hmacSha256(toSign, secret);
        return "t=" + timestamp + ",v1=" + signature;
    }

    public boolean verifySignature(String payload, String signatureHeader, String secret, long toleranceSeconds) {
        try {
            if (signatureHeader == null || !signatureHeader.contains("t=") || !signatureHeader.contains("v1=")) {
                return false;
            }

            long timestamp = -1;
            String expectedV1 = null;

            String[] parts = signatureHeader.split(",");
            for (String part : parts) {
                if (part.startsWith("t=")) {
                    timestamp = Long.parseLong(part.substring(2).trim());
                } else if (part.startsWith("v1=")) {
                    expectedV1 = part.substring(3).trim();
                }
            }

            if (timestamp == -1 || expectedV1 == null) {
                return false;
            }

            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > toleranceSeconds) {
                return false; // Replay protection: signature expired
            }

            String toSign = timestamp + "." + payload;
            String computed = hmacSha256(toSign, secret);
            return computed.equalsIgnoreCase(expectedV1);
        } catch (Exception e) {
            return false;
        }
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}

