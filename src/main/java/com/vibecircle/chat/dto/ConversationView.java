package com.vibecircle.chat.dto;

import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

public record ConversationView(Long id, AuthorDto otherUser, String lastMessagePreview, Long lastMessageSenderId,
                               Instant lastMessageAt, long unreadCount) {
}
