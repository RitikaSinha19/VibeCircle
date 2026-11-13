package com.vibecircle.chat;

public record ReadEvent(Long conversationId, Long readerId, Long otherId, Long upToMessageId) {
}
