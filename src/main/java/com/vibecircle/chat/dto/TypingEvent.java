package com.vibecircle.chat.dto;

public record TypingEvent(Long conversationId, Long userId, boolean typing) {
}
