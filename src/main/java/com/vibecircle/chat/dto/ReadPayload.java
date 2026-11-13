package com.vibecircle.chat.dto;

/** STOMP body for /app/chat/read */
public record ReadPayload(Long conversationId, Long upToMessageId) {
}
