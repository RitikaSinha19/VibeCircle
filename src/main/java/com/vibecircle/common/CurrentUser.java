package com.vibecircle.common;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {
    private CurrentUser() {}

    /** The JWT subject is the user's id (see JwtService). */
    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
