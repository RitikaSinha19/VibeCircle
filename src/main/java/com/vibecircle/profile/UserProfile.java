package com.vibecircle.profile;

import com.vibecircle.city.City;
import com.vibecircle.interest.Interest;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(length = 300)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false)
    private ProfileVisibility profileVisibility = ProfileVisibility.PUBLIC;

    @Column(nullable = false)
    private boolean discoverable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "friend_request_policy", nullable = false)
    private FriendRequestPolicy friendRequestPolicy = FriendRequestPolicy.EVERYONE;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "user_interests",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "interest_id"))
    private Set<Interest> interests = new HashSet<>();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    protected UserProfile() {}

    public Long getUserId() { return userId; }
    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public ProfileVisibility getProfileVisibility() { return profileVisibility; }
    public void setProfileVisibility(ProfileVisibility v) { this.profileVisibility = v; }
    public boolean isDiscoverable() { return discoverable; }
    public void setDiscoverable(boolean discoverable) { this.discoverable = discoverable; }
    public FriendRequestPolicy getFriendRequestPolicy() { return friendRequestPolicy; }
    public void setFriendRequestPolicy(FriendRequestPolicy p) { this.friendRequestPolicy = p; }
    public Set<Interest> getInterests() { return interests; }
}
