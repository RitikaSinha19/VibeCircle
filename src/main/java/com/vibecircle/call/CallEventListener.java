package com.vibecircle.call;

import com.vibecircle.call.dto.CallEvent;
import com.vibecircle.call.dto.CallView;
import com.vibecircle.chat.ChatPublisher;
import com.vibecircle.notification.NotificationPublisher;
import com.vibecircle.notification.NotificationService;
import com.vibecircle.notification.NotificationType;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes call state to the participants only AFTER the database commit. */
@Component
public class CallEventListener {

    private static final Logger log = LoggerFactory.getLogger(CallEventListener.class);
    private static final String QUEUE = "/queue/call";
    private static final Set<String> FINAL_STATES = Set.of("ENDED", "REJECTED", "CANCELLED", "MISSED", "FAILED");

    private final ChatPublisher publisher;
    private final SignalRateLimiter limiter;
    private final NotificationService notifications;
    private final NotificationPublisher notificationPublisher;

    public CallEventListener(ChatPublisher publisher, SignalRateLimiter limiter,
                             NotificationService notifications, NotificationPublisher notificationPublisher) {
        this.publisher = publisher;
        this.limiter = limiter;
        this.notifications = notifications;
        this.notificationPublisher = notificationPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CallStateEvent e) {
        CallView call = e.call();
        CallEvent event = CallEvent.state(e.type(), call);

        publisher.toUser(call.callee().id(), QUEUE, event);
        if (!"INCOMING".equals(e.type())) {                    // the caller already has the answer to their own request
            publisher.toUser(call.caller().id(), QUEUE, event);
        }
        if (FINAL_STATES.contains(e.type())) {
            limiter.forget(call.id());
        }
        if ("MISSED".equals(e.type())) {
            try {
                notifications.create(call.callee().id(), call.caller().id(), NotificationType.MISSED_CALL, "CALL", call.id())
                        .ifPresent(push -> notificationPublisher.push(call.callee().id(), push));
            } catch (RuntimeException ex) {
                log.warn("Could not create MISSED_CALL notification", ex);
            }
        }
    }
}
