package com.vibecircle.friendship.dto;

import java.time.Instant;

public record BlockedUserDto(Long userId, String username, String fullName, Instant blockedAt) {
}
