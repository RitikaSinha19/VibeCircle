package com.vibecircle.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibecircle.call.dto.CallEvent;
import com.vibecircle.call.dto.CallView;
import com.vibecircle.call.dto.IceConfig;
import com.vibecircle.call.dto.SignalPayload;
import com.vibecircle.chat.ChatPublisher;
import com.vibecircle.chat.PresenceService;
import com.vibecircle.common.ApiException;
import com.vibecircle.post.dto.AuthorDto;
import com.vibecircle.privacy.AccessPolicyService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/** Call rules in isolation: database, presence and the policy service are mocked. */
@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    private static final Long CALLER = 1L;
    private static final Long CALLEE = 2L;

    @Mock
    private CallRepository repo;
    @Mock
    private AccessPolicyService access;
    @Mock
    private PresenceService presence;
    @Mock
    private ChatPublisher publisher;
    @Mock
    private ApplicationEventPublisher events;

    private SignalRateLimiter limiter;
    private CallService service;

    private static CallProperties props(List<String> turnUrls, String turnSecret) {
        return new CallProperties(List.of("stun:stun.example.com:3478"), turnUrls, turnSecret,
                Duration.ofHours(1), Duration.ofSeconds(30), Duration.ofHours(2), Duration.ofSeconds(20), true);
    }

    @BeforeEach
    void setUp() {
        limiter = new SignalRateLimiter();
        service = new CallService(repo, access, presence, publisher, events, props(List.of(), ""), limiter);
    }

    private static CallView view(long id, CallStatus status) {
        return new CallView(id, CallType.VOICE, status, new AuthorDto(CALLER, "caller", "Caller"),
                new AuthorDto(CALLEE, "callee", "Callee"), Instant.now(), null, null, null);
    }

    private void assertFails(HttpStatus expected, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private String publishedEventType() {
        ArgumentCaptor<CallStateEvent> captor = ArgumentCaptor.forClass(CallStateEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue().type();
    }

    // ------------------------------------------------------------------ start
    @Test
    void cannotCallYourself() {
        assertFails(HttpStatus.BAD_REQUEST, () -> service.start(CALLER, CALLER, CallType.VOICE));
    }

    @Test
    void onlyFriendsCanBeCalled() {
        when(access.canMessage(CALLER, CALLEE)).thenReturn(false);

        assertFails(HttpStatus.FORBIDDEN, () -> service.start(CALLER, CALLEE, CallType.VOICE));
        verify(repo, never()).lockUsers(any(), any());
    }

    @Test
    void theCallerNeedsALiveConnectionToReceiveTheAnswer() {
        when(access.canMessage(CALLER, CALLEE)).thenReturn(true);
        when(presence.isOnline(CALLER)).thenReturn(false);

        assertFails(HttpStatus.CONFLICT, () -> service.start(CALLER, CALLEE, CallType.VOICE));
        verify(repo, never()).insertRinging(any(), any(), any());
    }

    @Test
    void youCannotStartASecondCallWhileInOne() {
        when(access.canMessage(CALLER, CALLEE)).thenReturn(true);
        when(presence.isOnline(CALLER)).thenReturn(true);
        when(repo.hasOpenCall(CALLER)).thenReturn(true);

        assertFails(HttpStatus.CONFLICT, () -> service.start(CALLER, CALLEE, CallType.VOICE));
        verify(repo, never()).insertRinging(any(), any(), any());
    }

    
    @Test
void aBusyCalleeIsRefused() {
    when(access.canMessage(CALLER, CALLEE)).thenReturn(true);
    when(presence.isOnline(CALLER)).thenReturn(true);

    when(repo.hasOpenCall(CALLER)).thenReturn(false);
    when(repo.hasOpenCall(CALLEE)).thenReturn(true);

    assertFails(HttpStatus.CONFLICT, () -> service.start(CALLER, CALLEE, CallType.VOICE));
    verify(repo, never()).insertRinging(any(), any(), any());
}
    @Test
    void anOnlineCalleeIsRungAndIncomingIsPublished() {
        when(access.canMessage(CALLER, CALLEE)).thenReturn(true);
        when(presence.isOnline(CALLER)).thenReturn(true);
        when(presence.isOnline(CALLEE)).thenReturn(true);
        when(repo.insertRinging(CALLER, CALLEE, CallType.VIDEO)).thenReturn(8L);
        when(repo.findView(8L)).thenReturn(Optional.of(view(8L, CallStatus.RINGING)));

        CallView result = service.start(CALLER, CALLEE, CallType.VIDEO);

        assertThat(result.status()).isEqualTo(CallStatus.RINGING);
        assertThat(publishedEventType()).isEqualTo("INCOMING");
        verify(repo).lockUsers(CALLER, CALLEE);
    }

    @Test
    void anOfflineCalleeProducesAMissedCallImmediately() {
        when(access.canMessage(CALLER, CALLEE)).thenReturn(true);
        when(presence.isOnline(CALLER)).thenReturn(true);
        when(presence.isOnline(CALLEE)).thenReturn(false);
        when(repo.insertMissed(CALLER, CALLEE, CallType.VOICE)).thenReturn(9L);
        when(repo.findView(9L)).thenReturn(Optional.of(view(9L, CallStatus.MISSED)));

        CallView result = service.start(CALLER, CALLEE, CallType.VOICE);

        assertThat(result.status()).isEqualTo(CallStatus.MISSED);
        assertThat(publishedEventType()).isEqualTo("MISSED");
        verify(repo, never()).insertRinging(any(), any(), any());
    }

    // ------------------------------------------------------------ transitions
    @Test
    void answeringSomeoneElsesCallLooksLikeItDoesNotExist() {
        assertFails(HttpStatus.NOT_FOUND, () -> service.accept(3L, 5L));
    }

    @Test
    void theCallerCannotAcceptTheirOwnCall() {
        when(repo.findOpenCall(5L)).thenReturn(Optional.of(new CallRepository.OpenCall(5L, CALLER, CALLEE, CallStatus.RINGING)));

        assertFails(HttpStatus.NOT_FOUND, () -> service.accept(CALLER, 5L));
        verify(events, never()).publishEvent(any(CallStateEvent.class));
    }

    @Test
    void answeringACallThatIsAlreadyClosedIsAConflictForTheCallee() {
        when(repo.isCallee(5L, CALLEE)).thenReturn(true);

        assertFails(HttpStatus.CONFLICT, () -> service.accept(CALLEE, 5L));
    }

    @Test
    void acceptIsRefusedOnceYouAreNoLongerFriends() {
        when(repo.findOpenCall(5L)).thenReturn(Optional.of(new CallRepository.OpenCall(5L, CALLER, CALLEE, CallStatus.RINGING)));
        when(access.canMessage(CALLEE, CALLER)).thenReturn(false);

        assertFails(HttpStatus.FORBIDDEN, () -> service.accept(CALLEE, 5L));
        verify(repo, never()).accept(any(), any());
    }

    @Test
    void acceptingActivatesTheCallAndPublishesIt() {
        when(repo.findOpenCall(5L)).thenReturn(Optional.of(new CallRepository.OpenCall(5L, CALLER, CALLEE, CallStatus.RINGING)));
        when(access.canMessage(CALLEE, CALLER)).thenReturn(true);
        when(repo.accept(5L, CALLEE)).thenReturn(Optional.of(5L));
        when(repo.findView(5L)).thenReturn(Optional.of(view(5L, CallStatus.ACTIVE)));

        assertThat(service.accept(CALLEE, 5L).status()).isEqualTo(CallStatus.ACTIVE);
        assertThat(publishedEventType()).isEqualTo("ACCEPTED");
    }

    @Test
    void endingACallThatIsNotActiveIsAConflictForParticipantsOnly() {
        when(repo.isParticipant(5L, CALLER)).thenReturn(true);

        assertFails(HttpStatus.CONFLICT, () -> service.end(CALLER, 5L));
        assertFails(HttpStatus.NOT_FOUND, () -> service.end(3L, 5L));
    }

    // -------------------------------------------------------------- signalling
    private static SignalPayload offer(long callId) {
        return new SignalPayload(callId, SignalType.OFFER, "{\"type\":\"offer\",\"sdp\":\"v=0\"}");
    }

    private void openCall() {
        when(repo.findOpenCall(5L)).thenReturn(Optional.of(new CallRepository.OpenCall(5L, CALLER, CALLEE, CallStatus.ACTIVE)));
    }

    @Test
    void signalsAreRelayedToTheOtherParticipantOnly() {
        openCall();
        when(access.canMessage(CALLER, CALLEE)).thenReturn(true);

        service.signal(CALLER, offer(5L));

        ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
        verify(publisher).toUser(eq(CALLEE), eq("/queue/call"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("SIGNAL");
        assertThat(captor.getValue().from()).isEqualTo(CALLER);
        assertThat(captor.getValue().signalType()).isEqualTo("OFFER");
        assertThat(captor.getValue().callId()).isEqualTo(5L);
    }

    @Test
    void outsidersCannotInjectSignals() {
        openCall();

        assertFails(HttpStatus.NOT_FOUND, () -> service.signal(3L, offer(5L)));
        verify(publisher, never()).toUser(any(), any(), any());
    }

    @Test
    void signalsToAClosedCallAreRefused() {
        assertFails(HttpStatus.NOT_FOUND, () -> service.signal(CALLER, offer(5L)));
    }

    @Test
    void malformedAndOversizedSignalsAreRefusedBeforeAnyLookup() {
        assertFails(HttpStatus.BAD_REQUEST, () -> service.signal(CALLER, null));
        assertFails(HttpStatus.BAD_REQUEST, () -> service.signal(CALLER, new SignalPayload(null, SignalType.OFFER, "x")));
        assertFails(HttpStatus.BAD_REQUEST, () -> service.signal(CALLER, new SignalPayload(5L, null, "x")));
        assertFails(HttpStatus.BAD_REQUEST, () -> service.signal(CALLER,
                new SignalPayload(5L, SignalType.OFFER, "x".repeat(CallService.MAX_SIGNAL_BYTES + 1))));
        verify(repo, never()).findOpenCall(any());
    }

    @Test
    void signallingStopsWhenThePeopleCanNoLongerCallEachOther() {
        openCall();
        when(access.canMessage(CALLER, CALLEE)).thenReturn(false);

        assertFails(HttpStatus.FORBIDDEN, () -> service.signal(CALLER, offer(5L)));
        verify(publisher, never()).toUser(any(), any(), any());
    }

    @Test
    void floodsAreRateLimited() {
        openCall();
        when(access.canMessage(CALLER, CALLEE)).thenReturn(true);
        for (int i = 0; i < SignalRateLimiter.LIMIT_PER_WINDOW; i++) {
            limiter.tryAcquire(5L, CALLER);
        }

        assertFails(HttpStatus.TOO_MANY_REQUESTS, () -> service.signal(CALLER, offer(5L)));
        verify(publisher, never()).toUser(any(), any(), any());
    }

    // -------------------------------------------------------------- ICE servers
    @Test
    void iceServersAreOnlyIssuedDuringACall() {
        assertFails(HttpStatus.CONFLICT, () -> service.iceServers(CALLER));
    }

    @Test
    void withoutATurnSecretOnlyStunIsReturned() {
        when(repo.hasOpenCall(CALLER)).thenReturn(true);

        IceConfig config = service.iceServers(CALLER);

        assertThat(config.iceServers()).hasSize(1);
        assertThat(config.iceServers().get(0).urls()).containsExactly("stun:stun.example.com:3478");
        assertThat(config.iceServers().get(0).username()).isNull();
    }

    @Test
    void withATurnSecretShortLivedCredentialsAreIssued() {
        CallService withTurn = new CallService(repo, access, presence, publisher, events,
                props(List.of("turn:turn.example.com:3478"), "shared-secret"), limiter);
        when(repo.hasOpenCall(CALLER)).thenReturn(true);

        IceConfig config = withTurn.iceServers(CALLER);

        assertThat(config.iceServers()).hasSize(2);
        var turn = config.iceServers().get(1);
        assertThat(turn.urls()).containsExactly("turn:turn.example.com:3478");
        assertThat(turn.username()).matches("\\d+:1");
        assertThat(turn.credential()).isNotBlank();
        assertThat(config.ttlSeconds()).isEqualTo(3600);
    }
}
