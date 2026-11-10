package com.vibecircle.post;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.post.dto.LikeResponse;
import com.vibecircle.post.dto.PostView;
import com.vibecircle.post.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    /** multipart/form-data: caption, audience (PUBLIC | FRIENDS_ONLY), cityId, files (repeatable) */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostView> create(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) String caption,
                                           @RequestParam(defaultValue = "PUBLIC") Audience audience,
                                           @RequestParam(required = false) Long cityId,
                                           @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(CurrentUser.id(jwt), caption, audience, cityId, files));
    }

    @GetMapping("/{id}")
    public PostView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/{id}")
    public PostView update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                           @Valid @RequestBody UpdatePostRequest req) {
        return service.update(CurrentUser.id(jwt), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/like")
    public LikeResponse like(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.like(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/{id}/like")
    public LikeResponse unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.unlike(CurrentUser.id(jwt), id);
    }
}
