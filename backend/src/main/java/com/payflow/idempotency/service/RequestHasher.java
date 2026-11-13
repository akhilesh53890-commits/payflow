package com.payflow.idempotency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Component
public class RequestHasher {

    private final ObjectMapper objectMapper;

    public RequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String computeHash(Object payload) {
        try {
            if (payload == null) {
                return computeSha256("");
            }
            if (payload instanceof String str) {
                return computeSha256(str.trim());
            }

            // Convert to canonical JSON with sorted map keys
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.convertValue(payload, Map.class);
            Map<String, Object> sortedMap = new TreeMap<>(map);
            String canonicalJson = objectMapper.writeValueAsString(sortedMap);
            return computeSha256(canonicalJson);
        } catch (Exception e) {
            return computeSha256(String.valueOf(payload));
        }
    }

    public String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 hash", e);
        }
    }
}

