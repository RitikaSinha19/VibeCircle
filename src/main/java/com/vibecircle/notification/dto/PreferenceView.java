package com.vibecircle.notification.dto;

import com.vibecircle.notification.NotificationType;

public record PreferenceView(NotificationType type, boolean enabled) {
}
