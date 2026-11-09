package com.vibecircle.auth.dto;

import com.vibecircle.user.Role;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserSummary user) {

    public record UserSummary(Long id, String username, String fullName, Role role) {
    }
}
