package com.vibecircle.chat.dto;

import jakarta.validation.constraints.NotNull;

public record OpenConversationRequest(@NotNull Long userId) {
}
