package com.vibecircle.call;

import com.vibecircle.call.dto.CallView;
import com.vibecircle.call.dto.IceConfig;
import com.vibecircle.call.dto.StartCallRequest;
import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final CallService service;

    public CallController(CallService service) {
        this.service = service;
    }

    /** Start a voice or video call with a friend. The callee's device rings. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CallView start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartCallRequest req) {
        return service.start(CurrentUser.id(jwt), req.calleeId(), req.type());
    }

    @GetMapping
    public PageResult<CallView> history(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.history(CurrentUser.id(jwt), page, size);
    }

    /** STUN/TURN servers for RTCPeerConnection; only available while you are in a call. */
    @GetMapping("/ice-servers")
    public IceConfig iceServers(@AuthenticationPrincipal Jwt jwt) {
        return service.iceServers(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public CallView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/accept")
    public CallView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.accept(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/reject")
    public CallView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.reject(CurrentUser.id(jwt), id);
    }

    /** The caller hangs up before the callee answers. */
    @PostMapping("/{id}/cancel")
    public CallView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.cancel(CurrentUser.id(jwt), id);
    }

    /** Either side ends an answered call. */
    @PostMapping("/{id}/end")
    public CallView end(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.end(CurrentUser.id(jwt), id);
    }

    /** Either side reports that the media connection failed. */
    @PostMapping("/{id}/failed")
    public CallView failed(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.fail(CurrentUser.id(jwt), id);
    }
}
