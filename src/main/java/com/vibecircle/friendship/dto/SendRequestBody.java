package com.vibecircle.friendship.dto;

import jakarta.validation.constraints.NotNull;

public record SendRequestBody(@NotNull Long receiverId) {
}
