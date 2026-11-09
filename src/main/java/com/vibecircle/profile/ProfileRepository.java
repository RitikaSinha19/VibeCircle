package com.vibecircle.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<UserProfile, Long> {

    /** Race-safe "create if missing": two simultaneous first requests cannot collide. */
    @Modifying
    @Query(value = "INSERT INTO user_profiles (user_id) VALUES (:userId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertIfMissing(@Param("userId") Long userId);
}
