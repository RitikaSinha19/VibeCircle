package com.vibecircle.chat;

import com.vibecircle.chat.dto.MessageView;

/** firstUnread = this is the first message the recipient has not read yet (drives the NEW_MESSAGE notification). */
public record MessageSentEvent(MessageView message, Long senderId, Long recipientId, boolean firstUnread) {
}
