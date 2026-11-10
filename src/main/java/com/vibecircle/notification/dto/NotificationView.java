package com.vibecircle.notification.dto;

import com.vibecircle.notification.NotificationType;
import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

/** entityType + entityId say what to open when clicked, e.g. POST 12 or FRIEND_REQUEST 7. */
public record NotificationView(Long id, NotificationType type, String message, AuthorDto actor,
                               String entityType, Long entityId, boolean read, Instant createdAt) {
}
