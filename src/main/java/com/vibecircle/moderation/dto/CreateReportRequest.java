package com.vibecircle.moderation.dto;

import com.vibecircle.moderation.ReportReason;
import com.vibecircle.moderation.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId,
        @NotNull ReportReason reason,
        @Size(max = 1000) String details) {
}
