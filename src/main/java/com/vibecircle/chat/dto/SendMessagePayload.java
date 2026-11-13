package com.vibecircle.chat.dto;

import java.util.UUID;

/** STOMP body for /app/chat/send */
public record SendMessagePayload(Long conversationId, String text, UUID clientMsgId) {
}
