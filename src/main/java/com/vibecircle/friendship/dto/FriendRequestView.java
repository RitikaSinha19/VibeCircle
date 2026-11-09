package com.vibecircle.friendship.dto;

import java.time.Instant;

/** For a received list, "other" is the sender; for a sent list, "other" is the receiver. */
public record FriendRequestView(Long id, Long otherUserId, String otherUsername, String otherFullName,
                                String status, Instant createdAt) {
}
