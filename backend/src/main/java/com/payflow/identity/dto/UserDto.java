package com.payflow.identity.dto;

import com.payflow.identity.domain.Role;
import com.payflow.identity.domain.User;

import java.time.Instant;

public record UserDto(
        String id,
        String email,
        Role role,
        String status,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}

