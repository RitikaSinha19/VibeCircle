package com.vibecircle.chat;

import com.vibecircle.chat.dto.ChatError;
import com.vibecircle.chat.dto.ReadPayload;
import com.vibecircle.chat.dto.SendMessagePayload;
import com.vibecircle.chat.dto.TypingPayload;
import com.vibecircle.common.ApiException;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * STOMP entry points (client SENDs to /app/chat/...). Every handler delegates to ChatService,
 * which does the membership and friendship checks, so the socket can never bypass the rules.
 */
@Controller
public class ChatStompController {

    private final ChatService chat;

    public ChatStompController(ChatService chat) {
        this.chat = chat;
    }

    @MessageMapping("/chat/send")
    public void send(Principal principal, @Payload SendMessagePayload payload) {
        chat.sendText(userId(principal), payload.conversationId(), payload.text(), payload.clientMsgId());
    }

    @MessageMapping("/chat/typing")
    public void typing(Principal principal, @Payload TypingPayload payload) {
        chat.typing(userId(principal), payload.conversationId(), payload.typing());
    }

    @MessageMapping("/chat/read")
    public void read(Principal principal, @Payload ReadPayload payload) {
        chat.markRead(userId(principal), payload.conversationId(), payload.upToMessageId());
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatError handle(ApiException e) {
        return new ChatError(e.getMessage());
    }

    private static Long userId(Principal principal) {
        return Long.valueOf(principal.getName());
    }
}
