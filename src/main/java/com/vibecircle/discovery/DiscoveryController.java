package com.vibecircle.discovery;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.discovery.dto.DiscoveryPage;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService service;

    public DiscoveryController(DiscoveryService service) {
        this.service = service;
    }

    /**
     * GET /api/discovery?page=0&size=20
     *   optional: interestId=14 (people with that interest), cityId=5 (browse another city)
     */
    @GetMapping
    public DiscoveryPage discover(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) Long interestId,
                                  @RequestParam(required = false) Long cityId) {
        return service.discover(CurrentUser.id(jwt), cityId, interestId, page, size);
    }

    @PostMapping("/hidden/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        service.hide(CurrentUser.id(jwt), userId);
    }

    @DeleteMapping("/hidden/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unhide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        service.unhide(CurrentUser.id(jwt), userId);
    }
}
