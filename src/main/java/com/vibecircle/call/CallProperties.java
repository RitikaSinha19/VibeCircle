package com.vibecircle.call;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * vibecircle.calls.stun-urls            STUN servers handed to clients (default: a public Google STUN, fine for development)
 * vibecircle.calls.turn-urls            your TURN server(s), e.g. turn:turn.example.com:3478?transport=udp
 * vibecircle.calls.turn-secret          shared secret with coturn (use-auth-secret); empty = no TURN credentials issued
 * vibecircle.calls.credential-ttl       how long a TURN credential is valid
 * vibecircle.calls.ring-timeout         unanswered calls become MISSED after this long
 * vibecircle.calls.max-duration         a call is force-ended after this long
 * vibecircle.calls.offline-grace        a participant may be disconnected this long before the call is ended
 * vibecircle.calls.require-presence     both people must have a live WebSocket to start a call (turn off only in tests)
 */
@ConfigurationProperties(prefix = "vibecircle.calls")
public record CallProperties(
        @DefaultValue("stun:stun.l.google.com:19302") List<String> stunUrls,
        @DefaultValue List<String> turnUrls,
        @DefaultValue("") String turnSecret,
        @DefaultValue("PT1H") Duration credentialTtl,
        @DefaultValue("PT30S") Duration ringTimeout,
        @DefaultValue("PT2H") Duration maxDuration,
        @DefaultValue("PT20S") Duration offlineGrace,
        @DefaultValue("true") boolean requirePresence) {
}
