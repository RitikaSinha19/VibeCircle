package com.vibecircle.moderation.dto;

import com.vibecircle.moderation.ModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveRequest(@NotNull ModerationAction action, @Size(max = 500) String resolution) {
}
