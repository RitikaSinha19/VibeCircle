package com.vibecircle.chat.dto;

/** STOMP body for /app/chat/typing */
public record TypingPayload(Long conversationId, boolean typing) {
}
