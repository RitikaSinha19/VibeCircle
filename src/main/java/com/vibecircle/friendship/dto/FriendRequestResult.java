package com.vibecircle.friendship.dto;

/** outcome = REQUEST_SENT, or FRIENDS when the other person had already asked us (auto-accepted). */
public record FriendRequestResult(String outcome, Long requestId) {
}
