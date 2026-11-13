package com.vibecircle.call;

import com.vibecircle.call.dto.CallView;
import com.vibecircle.call.dto.IceConfig;
import com.vibecircle.call.dto.IceServer;
import com.vibecircle.call.dto.SignalPayload;
import com.vibecircle.chat.ChatPublisher;
import com.vibecircle.chat.PresenceService;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.privacy.AccessPolicyService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Call control plane. The audio/video itself never touches the server: WebRTC sends it peer to peer
 * (or through a TURN relay). The server decides who may call whom, keeps the call state, and relays signalling.
 *
 * Rules: friends only (same rule as chat, no blocks); a user is in at most ONE open call at a time;
 * every state change is one atomic statement; closed calls are final.
 */
@Service
public class CallService {

    static final int MAX_SIGNAL_BYTES = 16_000;
    private static final String QUEUE = "/queue/call";

    private final CallRepository repo;
    private final AccessPolicyService access;
    private final PresenceService presence;
    private final ChatPublisher publisher;
    private final ApplicationEventPublisher events;
    private final CallProperties props;
    private final SignalRateLimiter limiter;

    /** when a participant of an open call was first seen offline (cleared when they come back) */
    private final Map<Long, Instant> offlineSince = new ConcurrentHashMap<>();

    public CallService(CallRepository repo, AccessPolicyService access, PresenceService presence,
                       ChatPublisher publisher, ApplicationEventPublisher events, CallProperties props,
                       SignalRateLimiter limiter) {
        this.repo = repo;
        this.access = access;
        this.presence = presence;
        this.publisher = publisher;
        this.events = events;
        this.props = props;
        this.limiter = limiter;
    }

    // ----------------------------------------------------------------- start
    @Transactional
    public CallView start(Long me, Long calleeId, CallType type) {
        if (me.equals(calleeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot call yourself");
        }
        if (!access.canMessage(me, calleeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only call your friends");
        }
        if (props.requirePresence() && !presence.isOnline(me)) {
            throw new ApiException(HttpStatus.CONFLICT, "Connect to the live channel before calling");
        }

        repo.lockUsers(me, calleeId);
        if (repo.hasOpenCall(me)) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already in a call");
        }
        if (repo.hasOpenCall(calleeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This user is busy");
        }

        boolean calleeOnline = !props.requirePresence() || presence.isOnline(calleeId);
        Long id = calleeOnline ? repo.insertRinging(me, calleeId, type) : repo.insertMissed(me, calleeId, type);
        CallView view = view(id);
        events.publishEvent(new CallStateEvent(calleeOnline ? "INCOMING" : "MISSED", view));
        return view;
    }

    // ----------------------------------------------------------- transitions
    @Transactional
    public CallView accept(Long me, Long callId) {
        repo.findOpenCall(callId).filter(c -> c.calleeId().equals(me)).ifPresent(c -> {
            if (!access.canMessage(me, c.callerId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can no longer call each other");
            }
        });
        repo.accept(callId, me).orElseThrow(() -> notActionable(callId, me, Role.CALLEE));
        return finish("ACCEPTED", callId);
    }

    @Transactional
    public CallView reject(Long me, Long callId) {
        repo.reject(callId, me).orElseThrow(() -> notActionable(callId, me, Role.CALLEE));
        return finish("REJECTED", callId);
    }

    @Transactional
    public CallView cancel(Long me, Long callId) {
        repo.cancel(callId, me).orElseThrow(() -> notActionable(callId, me, Role.CALLER));
        return finish("CANCELLED", callId);
    }

    @Transactional
    public CallView end(Long me, Long callId) {
        repo.end(callId, me).orElseThrow(() -> notActionable(callId, me, Role.EITHER));
        return finish("ENDED", callId);
    }

    @Transactional
    public CallView fail(Long me, Long callId) {
        repo.fail(callId, me).orElseThrow(() -> notActionable(callId, me, Role.EITHER));
        return finish("FAILED", callId);
    }

    private enum Role { CALLER, CALLEE, EITHER }

    /** 404 when it is not your call (never reveal it exists); 409 when it is yours but in the wrong state. */
    private ApiException notActionable(Long callId, Long me, Role role) {
        boolean party = switch (role) {
            case CALLER -> repo.isCaller(callId, me);
            case CALLEE -> repo.isCallee(callId, me);
            case EITHER -> repo.isParticipant(callId, me);
        };
        return party
                ? new ApiException(HttpStatus.CONFLICT, "This call cannot be changed in its current state")
                : new ApiException(HttpStatus.NOT_FOUND, "Call not found");
    }

    private CallView finish(String eventType, Long callId) {
        CallView view = view(callId);
        events.publishEvent(new CallStateEvent(eventType, view));
        return view;
    }

    private CallView view(Long id) {
        return repo.findView(id).orElseThrow(() -> new IllegalStateException("Call missing: " + id));
    }

    // ------------------------------------------------------------------ reads
    @Transactional(readOnly = true)
    public CallView get(Long me, Long callId) {
        if (!repo.isParticipant(callId, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Call not found");
        }
        return view(callId);
    }

    @Transactional(readOnly = true)
    public PageResult<CallView> history(Long me, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.history(me, s + 1, p * s), p, s);
    }

    /**
     * STUN/TURN servers for the browser. Handed out ONLY while the user is in a ringing or active call,
     * so the relay cannot be borrowed as free bandwidth, and TURN credentials expire on their own.
     */
    @Transactional(readOnly = true)
    public IceConfig iceServers(Long me) {
        if (!repo.hasOpenCall(me)) {
            throw new ApiException(HttpStatus.CONFLICT, "ICE servers are only issued during a call");
        }
        List<IceServer> servers = new ArrayList<>();
        if (!props.stunUrls().isEmpty()) {
            servers.add(new IceServer(props.stunUrls(), null, null));
        }
        if (!props.turnUrls().isEmpty() && !props.turnSecret().isBlank()) {
            long expires = Instant.now().plus(props.credentialTtl()).getEpochSecond();
            TurnCredentials.Credential c = TurnCredentials.generate(props.turnSecret(), me, expires);
            servers.add(new IceServer(props.turnUrls(), c.username(), c.credential()));
        }
        return new IceConfig(servers, props.credentialTtl().toSeconds());
    }

    // -------------------------------------------------------------- signalling
    /** Relays one signalling message to the other participant. Not stored. */
    public void signal(Long me, SignalPayload payload) {
        if (payload == null || payload.callId() == null || payload.type() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid signalling message");
        }
        if (payload.data() != null && payload.data().length() > MAX_SIGNAL_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Signalling message too large");
        }
        CallRepository.OpenCall call = repo.findOpenCall(payload.callId())
                .filter(c -> c.callerId().equals(me) || c.calleeId().equals(me))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Call not found"));
        Long other = call.callerId().equals(me) ? call.calleeId() : call.callerId();

        if (!access.canMessage(me, other)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can no longer call each other");
        }
        if (!limiter.tryAcquire(call.id(), me)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many signalling messages");
        }
        publisher.toUser(other, QUEUE, com.vibecircle.call.dto.CallEvent.signal(call.id(), me, payload.type(), payload.data()));
    }

    // ----------------------------------------------------------- janitor work
    /** Unanswered calls become MISSED. */
    @Transactional
    public int expireRinging() {
        List<Long> ids = repo.expireRinging(props.ringTimeout().toMillis() / 1000.0);
        ids.forEach(id -> events.publishEvent(new CallStateEvent("MISSED", view(id))));
        return ids.size();
    }

    /** Calls that ran past the maximum duration are ended. */
    @Transactional
    public int expireOverlong() {
        List<Long> ids = repo.expireActive(props.maxDuration().toMillis() / 1000.0);
        ids.forEach(id -> events.publishEvent(new CallStateEvent("ENDED", view(id))));
        return ids.size();
    }

    /**
     * Ends calls whose people can no longer call each other (unfriended or blocked mid-call) and calls where
     * someone has been disconnected longer than the grace period (so nobody is stuck "in a call" forever).
     */
    @Transactional
    public int sweepParticipants() {
        int ended = 0;
        Set<Long> openIds = new HashSet<>();
        for (CallRepository.OpenCall call : repo.findOpen()) {
            openIds.add(call.id());
            if (!access.canMessage(call.callerId(), call.calleeId())) {
                ended += forceEnd(call);
                continue;
            }
            if (!props.requirePresence()) {
                continue;
            }
            boolean callerOnline = presence.isOnline(call.callerId());
            boolean calleeOnline = presence.isOnline(call.calleeId());
            // while ringing only the caller has to stay: an absent callee is handled by the ring timeout
            boolean broken = call.status() == CallStatus.ACTIVE ? !(callerOnline && calleeOnline) : !callerOnline;
            if (!broken) {
                offlineSince.remove(call.id());
                continue;
            }
            Instant since = offlineSince.computeIfAbsent(call.id(), k -> Instant.now());
            if (Duration.between(since, Instant.now()).compareTo(props.offlineGrace()) > 0) {
                ended += forceEnd(call);
            }
        }
        offlineSince.keySet().retainAll(openIds);
        return ended;
    }

    private int forceEnd(CallRepository.OpenCall call) {
        CallStatus to = call.status() == CallStatus.ACTIVE ? CallStatus.ENDED : CallStatus.CANCELLED;
        Optional<Long> done = repo.forceEnd(call.id(), call.status(), to);
        done.ifPresent(id -> events.publishEvent(new CallStateEvent(to.name(), view(id))));
        return done.isPresent() ? 1 : 0;
    }
}
