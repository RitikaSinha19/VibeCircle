package com.vibecircle.notification;

import com.vibecircle.comment.NewCommentEvent;
import com.vibecircle.friendship.FriendRequestAcceptedEvent;
import com.vibecircle.friendship.FriendRequestSentEvent;
import com.vibecircle.post.PostLikedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into notifications. AFTER_COMMIT means a notification is only created
 * if the action really happened, and a failure here can never undo the user's action.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService service;
    private final NotificationPublisher publisher;

    public NotificationListener(NotificationService service, NotificationPublisher publisher) {
        this.service = service;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FriendRequestSentEvent e) {
        dispatch(e.receiverId(), e.senderId(), NotificationType.FRIEND_REQUEST, "FRIEND_REQUEST", e.requestId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FriendRequestAcceptedEvent e) {
        // the receiver accepted, so the original sender is told
        dispatch(e.senderId(), e.receiverId(), NotificationType.FRIEND_ACCEPTED, "FRIEND_REQUEST", e.requestId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostLikedEvent e) {
        dispatch(e.authorId(), e.likerId(), NotificationType.POST_LIKED, "POST", e.postId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NewCommentEvent e) {
        dispatch(e.postAuthorId(), e.commenterId(), NotificationType.NEW_COMMENT, "POST", e.postId());
    }

    private void dispatch(Long recipient, Long actor, NotificationType type, String entityType, Long entityId) {
        try {
            service.create(recipient, actor, type, entityType, entityId)
                    .ifPresent(push -> publisher.push(recipient, push));
        } catch (RuntimeException ex) {
            log.warn("Could not create {} notification for user {}", type, recipient, ex);
        }
    }
}
