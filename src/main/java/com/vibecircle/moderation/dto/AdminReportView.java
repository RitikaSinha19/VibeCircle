package com.vibecircle.moderation.dto;

import com.vibecircle.moderation.ReportReason;
import com.vibecircle.moderation.ReportStatus;
import com.vibecircle.moderation.ReportTargetType;
import com.vibecircle.post.dto.AuthorDto;
import java.time.Instant;

/** target is only filled on the detail endpoint (the list stays one query). */
public record AdminReportView(Long id, ReportTargetType targetType, Long targetId, ReportReason reason,
                              ReportStatus status, String details, AuthorDto reporter, long openReportsOnTarget,
                              String reviewedBy, String resolution, Instant createdAt, Instant reviewedAt,
                              TargetPreview target) {

    public AdminReportView withTarget(TargetPreview t) {
        return new AdminReportView(id, targetType, targetId, reason, status, details, reporter, openReportsOnTarget,
                reviewedBy, resolution, createdAt, reviewedAt, t);
    }
}
