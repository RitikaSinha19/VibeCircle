package com.vibecircle.call;

import com.vibecircle.call.dto.SignalPayload;
import com.vibecircle.chat.dto.ChatError;
import com.vibecircle.common.ApiException;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/** WebRTC signalling: the browser sends offers, answers and ICE candidates to /app/call/signal. */
@Controller
public class CallStompController {

    private final CallService calls;

    public CallStompController(CallService calls) {
        this.calls = calls;
    }

    @MessageMapping("/call/signal")
    public void signal(Principal principal, @Payload SignalPayload payload) {
        calls.signal(Long.valueOf(principal.getName()), payload);
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatError handle(ApiException e) {
        return new ChatError(e.getMessage());
    }
}
