package com.vibecircle.notification;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.notification.dto.NotificationPush;
import com.vibecircle.notification.dto.NotificationView;
import com.vibecircle.notification.dto.PreferenceView;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /**
     * REQUIRES_NEW: this runs from an AFTER_COMMIT listener, where the original transaction is
     * already finished, so the insert needs a transaction of its own to actually be committed.
     * Returns empty when the notification was intentionally skipped.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationPush> create(Long recipient, Long actor, NotificationType type,
                                             String entityType, Long entityId) {
        return repo.insert(recipient, actor, type, entityType, entityId)
                .flatMap(repo::findView)
                .map(view -> new NotificationPush(view, repo.unreadCount(recipient)));
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> list(Long me, boolean unreadOnly, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.list(me, unreadOnly, s + 1, p * s), p, s);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long me) {
        return repo.unreadCount(me);
    }

    @Transactional
    public void markRead(Long me, Long id) {
        if (!repo.markRead(id, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
        }
    }

    @Transactional
    public int markAllRead(Long me) {
        return repo.markAllRead(me);
    }

    @Transactional
    public void delete(Long me, Long id) {
        if (!repo.delete(id, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
        }
    }

    @Transactional(readOnly = true)
    public List<PreferenceView> preferences(Long me) {
        Set<NotificationType> disabled = repo.disabledTypes(me);
        return Arrays.stream(NotificationType.values())
                .map(t -> new PreferenceView(t, !disabled.contains(t))).toList();
    }

    @Transactional
    public void setPreference(Long me, NotificationType type, boolean enabled) {
        repo.setPreference(me, type, enabled);
    }
}
