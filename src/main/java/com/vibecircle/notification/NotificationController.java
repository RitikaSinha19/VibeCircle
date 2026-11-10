package com.vibecircle.notification;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import com.vibecircle.notification.dto.NotificationView;
import com.vibecircle.notification.dto.PreferenceView;
import com.vibecircle.notification.dto.UnreadCount;
import com.vibecircle.notification.dto.UpdatePreferenceRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<NotificationView> list(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "false") boolean unreadOnly,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.id(jwt), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(service.unreadCount(CurrentUser.id(jwt)));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.markRead(CurrentUser.id(jwt), id);
    }

    @PostMapping("/read-all")
    public UnreadCount markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(CurrentUser.id(jwt));
        return new UnreadCount(0);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/preferences")
    public List<PreferenceView> preferences(@AuthenticationPrincipal Jwt jwt) {
        return service.preferences(CurrentUser.id(jwt));
    }

    @PutMapping("/preferences/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPreference(@AuthenticationPrincipal Jwt jwt, @PathVariable NotificationType type,
                              @Valid @RequestBody UpdatePreferenceRequest req) {
        service.setPreference(CurrentUser.id(jwt), type, req.enabled());
    }
}
