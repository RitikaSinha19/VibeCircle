package com.vibecircle.profile;

import com.vibecircle.city.City;
import com.vibecircle.common.ApiException;
import com.vibecircle.friendship.FriendshipRepository;
import com.vibecircle.friendship.RelationshipInfo;
import com.vibecircle.interest.Interest;
import com.vibecircle.privacy.AccessPolicyService;
import com.vibecircle.privacy.ProfileAccess;
import com.vibecircle.profile.dto.CityDto;
import com.vibecircle.profile.dto.InterestDto;
import com.vibecircle.profile.dto.UserProfileView;
import com.vibecircle.user.User;
import com.vibecircle.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileViewService {

    private final AccessPolicyService access;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ProfileRepository profiles;

    public ProfileViewService(AccessPolicyService access, FriendshipRepository friendships,
                              UserRepository users, ProfileRepository profiles) {
        this.access = access;
        this.friendships = friendships;
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public UserProfileView view(Long viewerId, Long ownerId) {
        ProfileAccess level = access.profileAccess(viewerId, ownerId);
        if (level == ProfileAccess.NONE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        User owner = users.findById(ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        UserProfile profile = profiles.findById(ownerId).orElse(null);
        RelationshipInfo rel = friendships.relationship(viewerId, ownerId);
        ProfileVisibility visibility = profile == null ? ProfileVisibility.PUBLIC : profile.getProfileVisibility();

        String bio = null;
        CityDto city = null;
        List<InterestDto> interests = null;
        if (level == ProfileAccess.FULL && profile != null) {
            bio = profile.getBio();
            City c = profile.getCity();
            city = c == null ? null : new CityDto(c.getId(), c.getName(), c.getState(), c.getCountry());
            interests = profile.getInterests().stream()
                    .sorted(Comparator.comparing(Interest::getCategory).thenComparing(Interest::getName))
                    .map(i -> new InterestDto(i.getId(), i.getName(), i.getCategory()))
                    .toList();
        }
        return new UserProfileView(owner.getId(), owner.getUsername(), owner.getFullName(), level,
                rel.relationship(), rel.pendingRequestId(), visibility, bio, city, interests);
    }
}
