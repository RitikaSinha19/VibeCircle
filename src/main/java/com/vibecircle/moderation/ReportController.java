package com.vibecircle.moderation;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import com.vibecircle.moderation.dto.CreateReportRequest;
import com.vibecircle.moderation.dto.ReportView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** Report a USER, POST, COMMENT or MESSAGE. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateReportRequest req) {
        return service.create(CurrentUser.id(jwt), req);
    }

    @GetMapping("/mine")
    public PageResult<ReportView> mine(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.mine(CurrentUser.id(jwt), page, size);
    }
}
