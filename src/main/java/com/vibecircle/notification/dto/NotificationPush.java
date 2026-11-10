package com.vibecircle.notification.dto;

/** What is sent over WebSocket to /user/queue/notifications. */
public record NotificationPush(NotificationView notification, long unreadCount) {
}
