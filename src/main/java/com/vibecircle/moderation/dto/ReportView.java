package com.vibecircle.moderation.dto;

import com.vibecircle.moderation.ReportReason;
import com.vibecircle.moderation.ReportStatus;
import com.vibecircle.moderation.ReportTargetType;
import java.time.Instant;

/** What a reporter sees about their own report. */
public record ReportView(Long id, ReportTargetType targetType, Long targetId, ReportReason reason,
                         ReportStatus status, Instant createdAt) {
}
