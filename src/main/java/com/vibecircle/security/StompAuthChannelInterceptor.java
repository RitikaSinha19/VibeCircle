package com.vibecircle.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Authenticates and authorizes every STOMP frame.
 *  CONNECT   : must carry "Authorization: Bearer <access token>". The principal name becomes the user id.
 *  SUBSCRIBE : only /user/queue/** is allowed. Spring resolves "/user/..." per session, so a client can
 *              never listen on another user's queue, and cannot subscribe to broadcast destinations.
 *  SEND      : only to /app/chat/** (messages, typing, read receipts) and /app/call/** (call signalling) by authenticated users.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;

    public StompAuthChannelInterceptor(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        this.decoder = decoder;
        this.converter = converter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            requireUser(accessor);
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessageDeliveryException("Subscription not allowed");
            }
        } else if (StompCommand.SEND.equals(command)) {
            requireUser(accessor);
            String destination = accessor.getDestination();
            if (destination == null
                    || !(destination.startsWith("/app/chat/") || destination.startsWith("/app/call/"))) {
                throw new MessageDeliveryException("Sending is not allowed");
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            Jwt jwt = decoder.decode(header.substring(7).trim());
            Authentication auth = converter.convert(jwt);
            accessor.setUser(auth);
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private void requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
    }
}
