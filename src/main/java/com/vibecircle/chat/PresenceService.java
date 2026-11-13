package com.vibecircle.chat;

import com.vibecircle.chat.dto.ChatEvent;
import com.vibecircle.chat.dto.PresenceView;
import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Online = at least one live STOMP session. Kept in memory, so it is correct for ONE server instance;
 * when scaling out this moves to Redis (as planned in the PRD). Only friends ever see a user's presence.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();
    private final Map<Long, Integer> sessionCounts = new ConcurrentHashMap<>();
    private final JdbcClient jdbc;
    private final ChatRepository chatRepo;
    private final ChatPublisher publisher;

    public PresenceService(DataSource dataSource, ChatRepository chatRepo, ChatPublisher publisher) {
        this.jdbc = JdbcClient.create(dataSource);
        this.chatRepo = chatRepo;
        this.publisher = publisher;
    }

    public boolean isOnline(Long userId) {
        return sessionCounts.containsKey(userId);
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        Long userId = Long.valueOf(user.getName());
        sessionUsers.put(sessionId, userId);
        if (sessionCounts.merge(userId, 1, Integer::sum) == 1) {
            wentOnline(userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = sessionUsers.remove(event.getSessionId());
        if (userId == null) {
            return;
        }
        Integer left = sessionCounts.compute(userId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        if (left == null) {
            wentOffline(userId);
        }
    }

    private void wentOnline(Long userId) {
        try {
            // messages that were waiting for this user are now delivered; tell their senders
            Map<String, List<Long>> grouped = new HashMap<>();
            Map<String, Long[]> keys = new HashMap<>();
            chatRepo.markDeliveredFor(userId).forEach(r -> {
                String key = r.conversationId() + ":" + r.senderId();
                grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r.messageId());
                keys.put(key, new Long[]{r.conversationId(), r.senderId()});
            });
            grouped.forEach((key, ids) -> publisher.toUser(keys.get(key)[1], "/queue/messages",
                    ChatEvent.delivered(keys.get(key)[0], ids)));
            broadcast(userId, true, null);
        } catch (RuntimeException e) {
            log.warn("Presence (online) handling failed for user {}", userId, e);
        }
    }

    private void wentOffline(Long userId) {
        try {
            Instant now = Instant.now();
            jdbc.sql("UPDATE users SET last_seen_at = now() WHERE id = :id").param("id", userId).update();
            broadcast(userId, false, now);
        } catch (RuntimeException e) {
            log.warn("Presence (offline) handling failed for user {}", userId, e);
        }
    }

    private void broadcast(Long userId, boolean online, Instant lastSeen) {
        PresenceView view = new PresenceView(userId, online, lastSeen);
        friendIds(userId).forEach(friend -> publisher.toUser(friend, "/queue/presence", view));
    }

    private List<Long> friendIds(Long userId) {
        return jdbc.sql("""
                        SELECT CASE WHEN user_low_id = :me THEN user_high_id ELSE user_low_id END
                        FROM friendships WHERE user_low_id = :me OR user_high_id = :me
                        """)
                .param("me", userId).query(Long.class).list();
    }

    /** Presence of the given users, restricted to MY friends (anyone else is silently omitted). */
    public List<PresenceView> presenceOf(Long me, List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT u.id, u.last_seen_at FROM users u
                        WHERE u.id IN (:ids) AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                          AND EXISTS (SELECT 1 FROM friendships f
                                      WHERE f.user_low_id = LEAST(:me, u.id) AND f.user_high_id = GREATEST(:me, u.id))
                        """)
                .param("ids", userIds).param("me", me)
                .query((rs, i) -> {
                    OffsetDateTime seen = rs.getObject("last_seen_at", OffsetDateTime.class);
                    long id = rs.getLong("id");
                    return new PresenceView(id, isOnline(id), seen == null ? null : seen.toInstant());
                })
                .list();
    }
}
