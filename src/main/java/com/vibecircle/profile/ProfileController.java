package com.vibecircle.profile;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.profile.dto.ProfileResponse;
import com.vibecircle.profile.dto.UpdateInterestsRequest;
import com.vibecircle.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles/me")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.getMyProfile(CurrentUser.id(jwt));
    }

    @PatchMapping
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        return service.updateProfile(CurrentUser.id(jwt), req);
    }

    @PutMapping("/interests")
    public ProfileResponse replaceInterests(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody UpdateInterestsRequest req) {
        return service.replaceInterests(CurrentUser.id(jwt), req.interestIds());
    }
}
