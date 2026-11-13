package com.payflow.identity.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserDto user
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserDto user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}

