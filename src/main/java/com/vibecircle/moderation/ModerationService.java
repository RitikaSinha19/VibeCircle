package com.vibecircle.moderation;

import com.vibecircle.chat.UnsentEvent;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.media.MediaStorage;
import com.vibecircle.moderation.dto.AdminReportView;
import com.vibecircle.moderation.dto.TargetPreview;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Callers are checked for MODERATOR/ADMIN by @PreAuthorize on the controller. */
@Service
public class ModerationService {

    private final ModerationRepository repo;
    private final MediaStorage storage;
    private final ApplicationEventPublisher events;

    public ModerationService(ModerationRepository repo, MediaStorage storage, ApplicationEventPublisher events) {
        this.repo = repo;
        this.storage = storage;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminReportView> queue(ReportStatus status, ReportTargetType type, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        ReportStatus st = status == null ? ReportStatus.OPEN : status;
        return PageResult.of(repo.listAdmin(st, type, s + 1, p * s), p, s);
    }

    @Transactional(readOnly = true)
    public AdminReportView detail(Long reportId) {
        AdminReportView view = repo.findAdmin(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        return view.withTarget(repo.preview(view.targetType(), view.targetId()).orElse(null));
    }

    @Transactional
    public AdminReportView claim(Long moderatorId, Long reportId) {
        if (repo.claim(reportId, moderatorId).isEmpty()) {
            boolean exists = repo.findAdmin(reportId).isPresent();
            throw exists ? new ApiException(HttpStatus.CONFLICT, "This report is already being reviewed or is closed")
                         : new ApiException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return detail(reportId);
    }

    @Transactional
    public AdminReportView resolve(Long moderatorId, Long reportId, ModerationAction action, String rawResolution) {
        String resolution = rawResolution == null || rawResolution.isBlank() ? null : rawResolution.trim();

        ModerationRepository.ReportRow report = repo.lockReport(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.status() != ReportStatus.OPEN && report.status() != ReportStatus.UNDER_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "This report is already closed");
        }

        boolean removes = action == ModerationAction.REMOVE_CONTENT || action == ModerationAction.REMOVE_AND_SUSPEND;
        boolean suspends = action == ModerationAction.SUSPEND_USER || action == ModerationAction.REMOVE_AND_SUSPEND;
        if (removes && report.targetType() == ReportTargetType.USER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A user is not content. Use SUSPEND_USER instead");
        }

        TargetPreview target = repo.preview(report.targetType(), report.targetId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "The reported item no longer exists"));

        if (suspends) {
            suspendOwner(target.ownerId());
        }
        if (removes) {
            removeContent(report.targetType(), report.targetId());
        }

        ReportStatus finalStatus = action == ModerationAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.ACTIONED;
        repo.resolveAllForTarget(report.targetType(), report.targetId(), finalStatus, moderatorId, resolution);
        repo.logAction(moderatorId, action.name(), report.targetType(), report.targetId(), reportId, resolution);
        return detail(reportId);
    }

    /** Admin only (checked by the controller). */
    @Transactional
    public void unsuspend(Long adminId, Long userId) {
        if (!repo.unsuspendUser(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No suspended user with this id");
        }
        repo.logAction(adminId, "UNSUSPEND_USER", ReportTargetType.USER, userId, null, null);
    }

    // ---------------------------------------------------------------- helpers
    private void suspendOwner(Long ownerId) {
        String role = repo.userRole(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "The owner no longer exists"));
        if (!"USER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff accounts cannot be suspended from the report queue");
        }
        repo.suspendUser(ownerId);
    }

    private void removeContent(ReportTargetType type, Long targetId) {
        switch (type) {
            case POST -> repo.removePost(targetId);
            case COMMENT -> repo.removeComment(targetId);
            case MESSAGE -> repo.lockMessageForRemoval(targetId).ifPresent(msg -> {
                repo.removeMessage(targetId);
                Long other = repo.otherParticipant(msg.conversationId(), msg.senderId()).orElse(msg.senderId());
                events.publishEvent(new UnsentEvent(msg.conversationId(), targetId, msg.senderId(), other));
                if (msg.mediaKey() != null) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            storage.delete(msg.mediaKey());
                        }
                    });
                }
            });
            case USER -> throw new ApiException(HttpStatus.BAD_REQUEST, "A user is not content");
        }
    }
}
