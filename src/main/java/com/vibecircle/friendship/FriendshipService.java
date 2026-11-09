package com.vibecircle.friendship;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.friendship.dto.FriendDto;
import com.vibecircle.friendship.dto.FriendRequestResult;
import com.vibecircle.friendship.dto.FriendRequestView;
import com.vibecircle.privacy.AccessPolicyService;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {

    private final FriendshipRepository repo;
    private final AccessPolicyService access;
    private final ApplicationEventPublisher events;

    public FriendshipService(FriendshipRepository repo, AccessPolicyService access,
                             ApplicationEventPublisher events) {
        this.repo = repo;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public FriendRequestResult send(Long me, Long receiverId) {
        if (me.equals(receiverId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot send a request to yourself");
        }
        String policy = repo.findRequestPolicy(receiverId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (access.blockedEitherWay(me, receiverId)) {
            // same answer as "doesn't exist" so a block is never revealed
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (access.areFriends(me, receiverId)) {
            throw new ApiException(HttpStatus.CONFLICT, "You are already friends");
        }
        if (!"EVERYONE".equals(policy)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This user is not accepting friend requests");
        }

        // They already asked us? Then this is mutual: accept theirs instead of creating a second request.
        Optional<Long> reverse = repo.findPendingBetween(receiverId, me);
        if (reverse.isPresent()) {
            Long theirRequestId = reverse.get();
            Long senderId = repo.acceptByReceiver(theirRequestId, me)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Please try again"));
            completeAcceptance(theirRequestId, me, senderId);
            return new FriendRequestResult("FRIENDS", theirRequestId);
        }

        Long requestId;
        try {
            requestId = repo.insertRequest(me, receiverId);
        } catch (DataIntegrityViolationException e) {
            // partial unique indexes: a request between this pair is already pending
            throw new ApiException(HttpStatus.CONFLICT, "A friend request is already pending");
        }
        events.publishEvent(new FriendRequestSentEvent(requestId, me, receiverId));
        return new FriendRequestResult("REQUEST_SENT", requestId);
    }

    @Transactional
    public FriendDto accept(Long me, Long requestId) {
        Long senderId = repo.acceptByReceiver(requestId, me)
                .orElseThrow(() -> notActionable(requestId, me, true));
        return completeAcceptance(requestId, me, senderId);
    }

    @Transactional
    public void reject(Long me, Long requestId) {
        repo.rejectByReceiver(requestId, me).orElseThrow(() -> notActionable(requestId, me, true));
    }

    @Transactional
    public void cancel(Long me, Long requestId) {
        repo.cancelBySender(requestId, me).orElseThrow(() -> notActionable(requestId, me, false));
    }

    @Transactional
    public void removeFriend(Long me, Long friendId) {
        if (!repo.deleteFriendship(me, friendId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "You are not friends with this user");
        }
    }

    @Transactional(readOnly = true)
    public PageResult<FriendDto> friends(Long me, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.listFriends(me, s + 1, p * s), p, s);
    }

    @Transactional(readOnly = true)
    public PageResult<FriendRequestView> pending(Long me, boolean received, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.listPending(me, received, s + 1, p * s), p, s);
    }

    // ------------------------------------------------------------ helpers
    private FriendDto completeAcceptance(Long requestId, Long me, Long senderId) {
        repo.insertFriendship(me, senderId);
        events.publishEvent(new FriendRequestAcceptedEvent(requestId, senderId, me));
        return repo.findFriend(me, senderId)
                .orElseThrow(() -> new IllegalStateException("Friendship row missing after insert"));
    }

    /** 409 if it's your request but already handled; 404 if it isn't yours (don't reveal it exists). */
    private ApiException notActionable(Long requestId, Long me, boolean asReceiver) {
        boolean party = asReceiver ? repo.isReceiver(requestId, me) : repo.isSender(requestId, me);
        return party
                ? new ApiException(HttpStatus.CONFLICT, "This request is no longer pending")
                : new ApiException(HttpStatus.NOT_FOUND, "Request not found");
    }
}
