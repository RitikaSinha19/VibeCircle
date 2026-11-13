package com.vibecircle.moderation;

import com.vibecircle.chat.ChatRepository;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.moderation.dto.CreateReportRequest;
import com.vibecircle.moderation.dto.ReportView;
import com.vibecircle.privacy.AccessPolicyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    static final int MAX_REPORTS_PER_HOUR = 20;

    private final ModerationRepository repo;
    private final AccessPolicyService access;
    private final ChatRepository chat;

    public ReportService(ModerationRepository repo, AccessPolicyService access, ChatRepository chat) {
        this.repo = repo;
        this.access = access;
        this.chat = chat;
    }

    @Transactional
    public ReportView create(Long me, CreateReportRequest req) {
        if (repo.countRecentByReporter(me) >= MAX_REPORTS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many reports. Please try again later");
        }
        ModerationRepository.TargetRef target = repo.findActiveTarget(req.targetType(), req.targetId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Nothing to report here"));

        if (target.ownerId().equals(me)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot report yourself or your own content");
        }
        requireCanSee(me, req, target);

        String details = req.details() == null ? null : req.details().trim();
        if (details != null && details.isEmpty()) {
            details = null;
        }
        Long id;
        try {
            id = repo.insertReport(me, req.targetType(), req.targetId(), req.reason(), details);
        } catch (DataIntegrityViolationException e) {
            // uq_report_open: one open report per reporter per item
            throw new ApiException(HttpStatus.CONFLICT, "You already have an open report for this");
        }
        return repo.findMine(id, me).orElseThrow(() -> new IllegalStateException("Report missing after insert"));
    }

    /** You can only report what you could actually see. Anything else looks like it does not exist. */
    private void requireCanSee(Long me, CreateReportRequest req, ModerationRepository.TargetRef target) {
        boolean visible = switch (req.targetType()) {
            case USER -> true;                                   // reporting a user (even a blocked one) is allowed
            case POST, COMMENT -> access.canViewPost(me, target.postId());
            case MESSAGE -> chat.otherMember(target.conversationId(), me).isPresent();   // must be in the conversation
        };
        if (!visible) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nothing to report here");
        }
    }

    @Transactional(readOnly = true)
    public PageResult<ReportView> mine(Long me, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.listMine(me, s + 1, p * s), p, s);
    }
}
