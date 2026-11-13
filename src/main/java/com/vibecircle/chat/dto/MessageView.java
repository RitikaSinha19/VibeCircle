package com.vibecircle.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * status is only filled for messages YOU sent: SENT, DELIVERED or READ.
 * An unsent message is a tombstone: unsent = true, text and imageUrl are null.
 * imageUrl is an authenticated endpoint (GET with the Bearer token).
 */
public record MessageView(Long id, Long conversationId, Long senderId, String type, String text,
                          String imageUrl, boolean unsent, String status, UUID clientMsgId, Instant createdAt) {
}
