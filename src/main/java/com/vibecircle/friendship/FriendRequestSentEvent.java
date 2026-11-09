package com.vibecircle.friendship;

/** Published inside the transaction; the notification module will listen with @TransactionalEventListener. */
public record FriendRequestSentEvent(Long requestId, Long senderId, Long receiverId) {
}
