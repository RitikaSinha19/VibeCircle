package com.vibecircle.comment;

import com.vibecircle.comment.dto.CommentView;
import com.vibecircle.comment.dto.CreateCommentRequest;
import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CommentView> create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId,
                                              @Valid @RequestBody CreateCommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(CurrentUser.id(jwt), postId, req.body()));
    }

    @GetMapping
    public PageResult<CommentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.id(jwt), postId, page, size);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId, @PathVariable Long commentId) {
        service.delete(CurrentUser.id(jwt), postId, commentId);
    }
}
