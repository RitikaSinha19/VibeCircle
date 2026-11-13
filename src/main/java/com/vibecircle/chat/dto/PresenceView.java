package com.vibecircle.chat.dto;

import java.time.Instant;

public record PresenceView(Long userId, boolean online, Instant lastSeenAt) {
}
