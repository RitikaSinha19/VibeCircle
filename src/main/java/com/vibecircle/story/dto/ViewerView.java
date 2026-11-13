package com.vibecircle.story.dto;

import java.time.Instant;

public record ViewerView(Long userId, String username, String fullName, Instant viewedAt) {
}
