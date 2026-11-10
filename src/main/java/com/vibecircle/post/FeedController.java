package com.vibecircle.post;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.post.dto.FeedPage;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class FeedController {

    private final PostService service;

    public FeedController(PostService service) {
        this.service = service;
    }

    /** GET /api/feed?scope=ALL|FRIENDS&before=<nextCursor>&size=20  (newest first) */
    @GetMapping("/api/feed")
    public FeedPage feed(@AuthenticationPrincipal Jwt jwt,
                         @RequestParam(defaultValue = "ALL") FeedScope scope,
                         @RequestParam(required = false) Long before,
                         @RequestParam(defaultValue = "20") int size) {
        return service.feed(CurrentUser.id(jwt), scope, before, size);
    }

    /** Posts by one user, filtered to what the caller may see. */
    @GetMapping("/api/users/{userId}/posts")
    public FeedPage userPosts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
                              @RequestParam(required = false) Long before,
                              @RequestParam(defaultValue = "20") int size) {
        return service.postsBy(CurrentUser.id(jwt), userId, before, size);
    }
}
