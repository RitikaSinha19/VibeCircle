package com.vibecircle.profile;

import com.vibecircle.city.City;
import com.vibecircle.city.CityRepository;
import com.vibecircle.common.ApiException;
import com.vibecircle.interest.Interest;
import com.vibecircle.interest.InterestRepository;
import com.vibecircle.profile.dto.CityDto;
import com.vibecircle.profile.dto.InterestDto;
import com.vibecircle.profile.dto.ProfileResponse;
import com.vibecircle.profile.dto.UpdateProfileRequest;
import com.vibecircle.user.User;
import com.vibecircle.user.UserRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final UserRepository users;
    private final CityRepository cities;
    private final InterestRepository interests;

    public ProfileService(ProfileRepository profiles, UserRepository users,
                          CityRepository cities, InterestRepository interests) {
        this.profiles = profiles;
        this.users = users;
        this.cities = cities;
        this.interests = interests;
    }

    // DTO mapping happens inside these transactions because open-in-view is off
    // (lazy city / interests can only be read while the session is open).

    @Transactional
    public ProfileResponse getMyProfile(Long userId) {
        return toResponse(ensureProfile(userId), loadUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        UserProfile profile = ensureProfile(userId);

        if (req.bio() != null) {
            String bio = req.bio().trim();
            profile.setBio(bio.isEmpty() ? null : bio);
        }
        if (req.cityId() != null) {
            City city = cities.findById(req.cityId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown city"));
            profile.setCity(city);
        }
        if (req.profileVisibility() != null) profile.setProfileVisibility(req.profileVisibility());
        if (req.discoverable() != null) profile.setDiscoverable(req.discoverable());
        if (req.friendRequestPolicy() != null) profile.setFriendRequestPolicy(req.friendRequestPolicy());

        return toResponse(profile, loadUser(userId));
    }

    @Transactional
    public ProfileResponse replaceInterests(Long userId, List<Long> interestIds) {
        Set<Long> unique = new LinkedHashSet<>(interestIds);
        List<Interest> found = interests.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more interests do not exist");
        }

        UserProfile profile = ensureProfile(userId);
        // Set semantics: Hibernate only deletes/inserts the difference.
        profile.getInterests().retainAll(found);
        profile.getInterests().addAll(found);

        return toResponse(profile, loadUser(userId));
    }

    // ------------------------------------------------------------ helpers
    private UserProfile ensureProfile(Long userId) {
        return profiles.findById(userId).orElseGet(() -> {
            profiles.insertIfMissing(userId);
            return profiles.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Profile row missing after insert"));
        });
    }

    private User loadUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private ProfileResponse toResponse(UserProfile p, User u) {
        City c = p.getCity();
        CityDto city = c == null ? null : new CityDto(c.getId(), c.getName(), c.getState(), c.getCountry());
        List<InterestDto> mine = p.getInterests().stream()
                .sorted(Comparator.comparing(Interest::getCategory).thenComparing(Interest::getName))
                .map(i -> new InterestDto(i.getId(), i.getName(), i.getCategory()))
                .toList();
        boolean complete = city != null && !mine.isEmpty();
        return new ProfileResponse(u.getId(), u.getUsername(), u.getFullName(), p.getBio(), city,
                p.getProfileVisibility(), p.isDiscoverable(), p.getFriendRequestPolicy(), mine, complete);
    }
}
