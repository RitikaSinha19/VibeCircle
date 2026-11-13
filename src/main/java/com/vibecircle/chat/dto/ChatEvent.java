package com.vibecircle.chat.dto;

import java.util.List;

/**
 * Pushed to /user/queue/messages. type is one of:
 *   MESSAGE   (message filled)             a new message, also echoed to the sender
 *   READ      (userId, upToMessageId)      userId has read everything up to that message
 *   DELIVERED (messageIds)                 those messages reached the recipient
 *   UNSENT    (messageIds)                 the sender unsent those messages
 */
public record ChatEvent(String type, Long conversationId, MessageView message, Long userId,
                        Long upToMessageId, List<Long> messageIds) {

    public static ChatEvent message(MessageView m) {
        return new ChatEvent("MESSAGE", m.conversationId(), m, null, null, null);
    }

    public static ChatEvent read(Long conversationId, Long readerId, Long upTo) {
        return new ChatEvent("READ", conversationId, null, readerId, upTo, null);
    }

    public static ChatEvent delivered(Long conversationId, List<Long> messageIds) {
        return new ChatEvent("DELIVERED", conversationId, null, null, null, messageIds);
    }

    public static ChatEvent unsent(Long conversationId, Long messageId) {
        return new ChatEvent("UNSENT", conversationId, null, null, null, List.of(messageId));
    }
}
