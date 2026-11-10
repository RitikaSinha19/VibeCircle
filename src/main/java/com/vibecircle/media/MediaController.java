package com.vibecircle.media;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.CurrentUser;
import com.vibecircle.privacy.AccessPolicyService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media is NOT served from a public folder. Every read re-checks the post's visibility,
 * so changing a post to friends-only (or blocking someone) cuts off access immediately.
 * Video Range requests work automatically because we return a Resource.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaRepository media;
    private final MediaStorage storage;
    private final AccessPolicyService access;

    public MediaController(MediaRepository media, MediaStorage storage, AccessPolicyService access) {
        this.media = media;
        this.storage = storage;
        this.access = access;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long me = CurrentUser.id(jwt);
        var record = media.find(id)
                .filter(m -> access.canViewPost(me, m.postId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Media not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(storage.load(record.storageKey()));
    }
}
