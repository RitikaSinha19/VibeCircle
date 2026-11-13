package com.vibecircle.story;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import com.vibecircle.post.Audience;
import com.vibecircle.story.dto.StoryView;
import com.vibecircle.story.dto.TrayItem;
import com.vibecircle.story.dto.ViewerView;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class StoryController {

    private final StoryService service;

    public StoryController(StoryService service) {
        this.service = service;
    }

    /** multipart/form-data: text (optional), audience (PUBLIC | FRIENDS_ONLY, default FRIENDS_ONLY), file (optional) */
    @PostMapping(value = "/api/stories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryView> create(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) String text,
                                            @RequestParam(defaultValue = "FRIENDS_ONLY") Audience audience,
                                            @RequestParam(name = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(jwt), text, audience, file));
    }

    /** Authors (me + friends) who have an active story I may see, unseen ones first. */
    @GetMapping("/api/stories/tray")
    public PageResult<TrayItem> tray(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.tray(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/api/stories/mine")
    public List<StoryView> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(CurrentUser.id(jwt));
    }

    /** One user's active stories, oldest first (play order), filtered to what I may see. */
    @GetMapping("/api/users/{userId}/stories")
    public List<StoryView> byUser(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return service.byUser(CurrentUser.id(jwt), userId);
    }

    @GetMapping("/api/stories/{id}")
    public StoryView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    /** Call when the story is actually shown. Idempotent; the author's own views are not counted. */
    @PostMapping("/api/stories/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.recordView(CurrentUser.id(jwt), id);
    }

    /** Author only. */
    @GetMapping("/api/stories/{id}/viewers")
    public PageResult<ViewerView> viewers(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.viewers(CurrentUser.id(jwt), id, page, size);
    }

    @GetMapping("/api/stories/{id}/media")
    public ResponseEntity<Resource> media(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        StoryService.StoryMedia media = service.openMedia(CurrentUser.id(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(media.resource());
    }

    @DeleteMapping("/api/stories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
    }
}
