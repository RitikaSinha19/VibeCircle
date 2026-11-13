package com.vibecircle.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibecircle.common.ApiException;
import com.vibecircle.friendship.dto.FriendDto;
import com.vibecircle.friendship.dto.FriendRequestResult;
import com.vibecircle.privacy.AccessPolicyService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** Business rules in isolation: the database and the policy service are mocked. */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    private static final Long ME = 1L;
    private static final Long THEM = 2L;

    @Mock
    private FriendshipRepository repo;
    @Mock
    private AccessPolicyService access;
    @Mock
    private ApplicationEventPublisher events;
    @InjectMocks
    private FriendshipService service;

    private void assertFails(HttpStatus expected, Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    @Test
    void cannotRequestYourself() {
        assertFails(HttpStatus.BAD_REQUEST, () -> service.send(ME, ME));
        verify(repo, never()).insertRequest(any(), any());
    }

    @Test
    void unknownUserIsNotFound() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.empty());
        assertFails(HttpStatus.NOT_FOUND, () -> service.send(ME, THEM));
    }

    @Test
    void aBlockLooksLikeANonExistentUser() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.of("EVERYONE"));
        when(access.blockedEitherWay(ME, THEM)).thenReturn(true);

        assertFails(HttpStatus.NOT_FOUND, () -> service.send(ME, THEM));
        verify(repo, never()).insertRequest(any(), any());
    }

    @Test
    void existingFriendsConflict() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.of("EVERYONE"));
        when(access.areFriends(ME, THEM)).thenReturn(true);

        assertFails(HttpStatus.CONFLICT, () -> service.send(ME, THEM));
    }

    @Test
    void usersWhoDisabledRequestsAreForbidden() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.of("NOBODY"));

        assertFails(HttpStatus.FORBIDDEN, () -> service.send(ME, THEM));
        verify(repo, never()).insertRequest(any(), any());
    }

    @Test
    void aFreshRequestIsStoredAndAnEventIsPublished() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.of("EVERYONE"));
        when(repo.findPendingBetween(THEM, ME)).thenReturn(Optional.empty());
        when(repo.insertRequest(ME, THEM)).thenReturn(5L);

        FriendRequestResult result = service.send(ME, THEM);

        assertThat(result.outcome()).isEqualTo("REQUEST_SENT");
        assertThat(result.requestId()).isEqualTo(5L);
        verify(events).publishEvent(new FriendRequestSentEvent(5L, ME, THEM));
    }

    @Test
    void ifTheyAlreadyAskedUsTheRequestsMergeIntoAFriendship() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.of("EVERYONE"));
        when(repo.findPendingBetween(THEM, ME)).thenReturn(Optional.of(77L));
        when(repo.acceptByReceiver(77L, ME)).thenReturn(Optional.of(THEM));
        when(repo.findFriend(ME, THEM)).thenReturn(Optional.of(new FriendDto(THEM, "them", "Them", Instant.now())));

        FriendRequestResult result = service.send(ME, THEM);

        assertThat(result.outcome()).isEqualTo("FRIENDS");
        verify(repo).insertFriendship(ME, THEM);
        verify(repo, never()).insertRequest(any(), any());
    }

    @Test
    void aLostRaceOnTheUniqueIndexBecomesAConflictNotAServerError() {
        when(repo.findRequestPolicy(THEM)).thenReturn(Optional.of("EVERYONE"));
        when(repo.findPendingBetween(THEM, ME)).thenReturn(Optional.empty());
        when(repo.insertRequest(ME, THEM)).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertFails(HttpStatus.CONFLICT, () -> service.send(ME, THEM));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void acceptingSomeoneElsesRequestLooksLikeItDoesNotExist() {
        when(repo.acceptByReceiver(9L, ME)).thenReturn(Optional.empty());
        when(repo.isReceiver(9L, ME)).thenReturn(false);

        assertFails(HttpStatus.NOT_FOUND, () -> service.accept(ME, 9L));
        verify(repo, never()).insertFriendship(any(), any());
    }

    @Test
    void acceptingAnAlreadyHandledRequestIsAConflict() {
        when(repo.acceptByReceiver(9L, ME)).thenReturn(Optional.empty());
        when(repo.isReceiver(9L, ME)).thenReturn(true);

        assertFails(HttpStatus.CONFLICT, () -> service.accept(ME, 9L));
    }

    @Test
    void acceptingCreatesTheFriendshipAndPublishesAnEvent() {
        FriendDto friend = new FriendDto(THEM, "them", "Them", Instant.now());
        when(repo.acceptByReceiver(9L, ME)).thenReturn(Optional.of(THEM));
        when(repo.findFriend(ME, THEM)).thenReturn(Optional.of(friend));

        assertThat(service.accept(ME, 9L)).isEqualTo(friend);
        verify(repo).insertFriendship(ME, THEM);
        verify(events).publishEvent(new FriendRequestAcceptedEvent(9L, THEM, ME));
    }
}
