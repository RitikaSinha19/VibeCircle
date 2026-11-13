package com.vibecircle.chat;

import com.vibecircle.chat.dto.ChatEvent;
import com.vibecircle.notification.NotificationPublisher;
import com.vibecircle.notification.NotificationService;
import com.vibecircle.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes only AFTER the database commit, so a client never hears about a message that was rolled back. */
@Component
public class ChatEventListener {

    private static final Logger log = LoggerFactory.getLogger(ChatEventListener.class);
    private static final String QUEUE = "/queue/messages";

    private final ChatPublisher publisher;
    private final NotificationService notifications;
    private final NotificationPublisher notificationPublisher;

    public ChatEventListener(ChatPublisher publisher, NotificationService notifications,
                             NotificationPublisher notificationPublisher) {
        this.publisher = publisher;
        this.notifications = notifications;
        this.notificationPublisher = notificationPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MessageSentEvent e) {
        ChatEvent event = ChatEvent.message(e.message());
        publisher.toUser(e.recipientId(), QUEUE, event);
        publisher.toUser(e.senderId(), QUEUE, event);          // echo, so other tabs/devices stay in sync
        if (e.firstUnread()) {
            // one notification per unread streak, not one per message
            try {
                notifications.create(e.recipientId(), e.senderId(), NotificationType.NEW_MESSAGE,
                                "CONVERSATION", e.message().conversationId())
                        .ifPresent(push -> notificationPublisher.push(e.recipientId(), push));
            } catch (RuntimeException ex) {
                log.warn("Could not create NEW_MESSAGE notification", ex);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReadEvent e) {
        ChatEvent event = ChatEvent.read(e.conversationId(), e.readerId(), e.upToMessageId());
        publisher.toUser(e.otherId(), QUEUE, event);
        publisher.toUser(e.readerId(), QUEUE, event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UnsentEvent e) {
        ChatEvent event = ChatEvent.unsent(e.conversationId(), e.messageId());
        publisher.toUser(e.otherId(), QUEUE, event);
        publisher.toUser(e.senderId(), QUEUE, event);
    }
}
