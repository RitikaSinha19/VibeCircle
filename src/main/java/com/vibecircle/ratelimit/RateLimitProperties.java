package com.vibecircle.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * vibecircle.ratelimit.*   (all counts are per window; keys are the user id when logged in, otherwise the client IP)
 *   enabled                    master switch (tests turn it off)
 *   auth-per-minute            login / register / refresh / password reset, per IP
 *   signup-per-hour            new accounts per IP
 *   reset-per-hour             password-reset requests per IP
 *   reads-per-minute           all GET requests, per user
 *   writes-per-minute          all POST / PUT / PATCH / DELETE requests, per user
 *   uploads-per-quarter-hour   multipart uploads (posts, stories, chat images), per user
 *   friend-requests-per-hour   friend requests, per user
 *   stomp-per-minute           messages a user may SEND over the WebSocket (chat, typing, call signalling)
 */
@ConfigurationProperties(prefix = "vibecircle.ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int authPerMinute,
        @DefaultValue("10") int signupPerHour,
        @DefaultValue("5") int resetPerHour,
        @DefaultValue("600") int readsPerMinute,
        @DefaultValue("120") int writesPerMinute,
        @DefaultValue("30") int uploadsPerQuarterHour,
        @DefaultValue("30") int friendRequestsPerHour,
        @DefaultValue("240") int stompPerMinute) {
}
