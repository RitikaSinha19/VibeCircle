package com.vibecircle.friendship;

public record FriendRequestAcceptedEvent(Long requestId, Long senderId, Long receiverId) {
}
