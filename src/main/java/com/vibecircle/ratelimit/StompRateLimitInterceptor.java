package com.vibecircle.ratelimit;

import java.security.Principal;
import java.time.Duration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/** HTTP limits do not cover the socket, so chat, typing and call-signalling floods are capped here. */
public class StompRateLimitInterceptor implements ChannelInterceptor {

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    public StompRateLimitInterceptor(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (!props.enabled()) {
            return message;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SEND.equals(accessor.getCommand())) {
            return message;
        }
        Principal user = accessor.getUser();
        if (user != null && !limiter.tryAcquire("stomp:" + user.getName(), props.stompPerMinute(), Duration.ofMinutes(1)).allowed()) {
            throw new MessageDeliveryException("Rate limit exceeded");
        }
        return message;
    }
}
