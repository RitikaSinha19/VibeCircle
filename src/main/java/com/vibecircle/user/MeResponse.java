package com.vibecircle.user;

public record MeResponse(Long id, String username, String email, String fullName, Role role, UserStatus status) {
    public static MeResponse from(User u) {
        return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus());
    }
}
