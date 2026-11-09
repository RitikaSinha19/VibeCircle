package com.vibecircle.friendship;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import com.vibecircle.friendship.dto.FriendDto;
import com.vibecircle.friendship.dto.FriendRequestResult;
import com.vibecircle.friendship.dto.FriendRequestView;
import com.vibecircle.friendship.dto.SendRequestBody;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    private final FriendshipService service;

    public FriendshipController(FriendshipService service) {
        this.service = service;
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendRequestResult> send(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody SendRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.send(CurrentUser.id(jwt), body.receiverId()));
    }

    @GetMapping("/requests/received")
    public PageResult<FriendRequestView> received(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.pending(CurrentUser.id(jwt), true, page, size);
    }

    @GetMapping("/requests/sent")
    public PageResult<FriendRequestView> sent(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.pending(CurrentUser.id(jwt), false, page, size);
    }

    @PostMapping("/requests/{id}/accept")
    public FriendDto accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.accept(CurrentUser.id(jwt), id);
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.reject(CurrentUser.id(jwt), id);
    }

    @PostMapping("/requests/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.cancel(CurrentUser.id(jwt), id);
    }

    @GetMapping
    public PageResult<FriendDto> friends(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.friends(CurrentUser.id(jwt), page, size);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        service.removeFriend(CurrentUser.id(jwt), userId);
    }
}
