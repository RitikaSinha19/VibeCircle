package com.vibecircle.notification;

import com.vibecircle.notification.dto.NotificationPush;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes to ONE user's own queue. The STOMP principal name is the user id (see StompAuthChannelInterceptor). */
@Component
public class NotificationPublisher {

    private final SimpMessagingTemplate template;

    public NotificationPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void push(Long recipientId, NotificationPush payload) {
        template.convertAndSendToUser(String.valueOf(recipientId), "/queue/notifications", payload);
    }
}
