package com.vibecircle.profile;

import com.vibecircle.common.CurrentUser;
import com.vibecircle.profile.dto.UserProfileView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class ProfileViewController {

    private final ProfileViewService service;

    public ProfileViewController(ProfileViewService service) {
        this.service = service;
    }

    /** View another user's profile. Blocked or unknown users get 404; private profiles get a limited view. */
    @GetMapping("/{userId}/profile")
    public UserProfileView view(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return service.view(CurrentUser.id(jwt), userId);
    }
}
