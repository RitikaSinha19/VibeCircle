package com.vibecircle.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Sends to one user's own queue. The STOMP principal name is the user id. */
@Component
public class ChatPublisher {

    private final SimpMessagingTemplate template;

    public ChatPublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void toUser(Long userId, String destination, Object payload) {
        template.convertAndSendToUser(String.valueOf(userId), destination, payload);
    }
}
