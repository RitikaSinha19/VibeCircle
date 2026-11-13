package com.vibecircle.call.dto;

import com.vibecircle.call.CallType;
import jakarta.validation.constraints.NotNull;

public record StartCallRequest(@NotNull Long calleeId, @NotNull CallType type) {
}
