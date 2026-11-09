package com.vibecircle.friendship.dto;

import java.time.Instant;

public record FriendDto(Long userId, String username, String fullName, Instant since) {
}
