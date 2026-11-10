package com.vibecircle.notification.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePreferenceRequest(@NotNull Boolean enabled) {
}
