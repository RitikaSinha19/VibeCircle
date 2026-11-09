package com.vibecircle.friendship;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.friendship.dto.BlockedUserDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockService {

    private final FriendshipRepository repo;

    public BlockService(FriendshipRepository repo) {
        this.repo = repo;
    }

    /** Blocking = record the block, end any friendship, cancel pending requests. Idempotent. */
    @Transactional
    public void block(Long me, Long target) {
        if (me.equals(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot block yourself");
        }
        if (!repo.userExists(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        repo.insertBlock(me, target);
        repo.deleteFriendship(me, target);
        repo.cancelPendingBetween(me, target);
    }

    /** Unblocking does not restore the old friendship. Idempotent. */
    @Transactional
    public void unblock(Long me, Long target) {
        repo.deleteBlock(me, target);
    }

    @Transactional(readOnly = true)
    public PageResult<BlockedUserDto> blocked(Long me, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.listBlocked(me, s + 1, p * s), p, s);
    }
}
