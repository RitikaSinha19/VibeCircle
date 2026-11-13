package com.vibecircle.chat;

public record UnsentEvent(Long conversationId, Long messageId, Long senderId, Long otherId) {
}
