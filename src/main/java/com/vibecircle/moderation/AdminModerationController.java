package com.vibecircle.moderation;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import com.vibecircle.moderation.dto.AdminReportView;
import com.vibecircle.moderation.dto.ResolveRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Staff only. The role comes from the signed JWT ("role" claim -> ROLE_MODERATOR / ROLE_ADMIN),
 * so a normal user gets 403 on every endpoint here.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
public class AdminModerationController {

    private final ModerationService service;

    public AdminModerationController(ModerationService service) {
        this.service = service;
    }

    /** GET /api/admin/reports?status=OPEN&targetType=POST  (status defaults to OPEN) */
    @GetMapping("/reports")
    public PageResult<AdminReportView> queue(@RequestParam(required = false) ReportStatus status,
                                             @RequestParam(required = false) ReportTargetType targetType,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.queue(status, targetType, page, size);
    }

    @GetMapping("/reports/{id}")
    public AdminReportView detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/reports/{id}/claim")
    public AdminReportView claim(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.claim(CurrentUser.id(jwt), id);
    }

    @PostMapping("/reports/{id}/resolve")
    public AdminReportView resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody ResolveRequest req) {
        return service.resolve(CurrentUser.id(jwt), id, req.action(), req.resolution());
    }

    @PostMapping("/users/{userId}/unsuspend")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsuspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        service.unsuspend(CurrentUser.id(jwt), userId);
    }
}
