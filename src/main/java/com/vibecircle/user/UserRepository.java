package com.vibecircle.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // lower(...) matches the unique indexes uq_users_username_lower / uq_users_email_lower
    @Query("select u from User u where lower(u.username) = lower(:v) and u.deletedAt is null")
    Optional<User> findByUsername(@Param("v") String username);

    @Query("select u from User u where lower(u.email) = lower(:v) and u.deletedAt is null")
    Optional<User> findByEmail(@Param("v") String email);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:v)")
    boolean usernameTaken(@Param("v") String username);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:v)")
    boolean emailTaken(@Param("v") String email);
}
