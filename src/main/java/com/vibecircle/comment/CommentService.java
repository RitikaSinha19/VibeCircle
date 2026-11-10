package com.vibecircle.comment;

import com.vibecircle.comment.dto.CommentView;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.privacy.AccessPolicyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository repo;
    private final AccessPolicyService access;
    private final ApplicationEventPublisher events;

    public CommentService(CommentRepository repo, AccessPolicyService access, ApplicationEventPublisher events) {
        this.repo = repo;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public CommentView create(Long me, Long postId, String body) {
        requireVisible(me, postId);
        String text = body.trim();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Comment cannot be empty");
        }
        Long id = repo.insert(postId, me, text);
        repo.postAuthor(postId).filter(a -> !a.equals(me))
                .ifPresent(a -> events.publishEvent(new NewCommentEvent(id, postId, me, a)));
        return repo.findView(id, me)
                .orElseThrow(() -> new IllegalStateException("Comment missing after insert"));
    }

    @Transactional(readOnly = true)
    public PageResult<CommentView> list(Long me, Long postId, int page, int size) {
        requireVisible(me, postId);
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.list(postId, me, s + 1, p * s), p, s);
    }

    @Transactional
    public void delete(Long me, Long postId, Long commentId) {
        if (!repo.softDelete(commentId, postId, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Comment not found");   // missing or not yours
        }
    }

    private void requireVisible(Long me, Long postId) {
        if (!access.canViewPost(me, postId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }
}
