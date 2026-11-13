package com.vibecircle.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthApiTest extends ApiTestBase {

    private Resp register(String username, String email, String password, String dob) {
        return api.post("/api/auth/register", null, json("username", username, "email", email,
                "password", password, "fullName", "Auth Test", "dateOfBirth", dob));
    }

    @Test
    void registerLoginAndCallProtectedEndpoint() {
        String u = uniqueName("reg");
        expect(register(u, u + "@example.test", PASSWORD, "1999-01-01"), 201);

        Resp login = expect(api.post("/api/auth/login", null, json("identifier", u, "password", PASSWORD)), 200);
        assertThat(login.text("accessToken")).isNotBlank();
        assertThat(login.text("refreshToken")).isNotBlank();

        Resp me = expect(api.get("/api/users/me", login.text("accessToken")), 200);
        assertThat(me.text("username")).isEqualTo(u);
        assertThat(me.text("role")).isEqualTo("USER");
    }

    @Test
    void loginWorksWithEmailToo() {
        String u = uniqueName("mail");
        expect(register(u, u + "@example.test", PASSWORD, "1999-01-01"), 201);
        expect(api.post("/api/auth/login", null, json("identifier", u + "@EXAMPLE.test", "password", PASSWORD)), 200);
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitively() {
        String u = uniqueName("dupu");
        expect(register(u, u + "@example.test", PASSWORD, "1999-01-01"), 201);
        expect(register(u.toUpperCase(), "other_" + u + "@example.test", PASSWORD, "1999-01-01"), 409);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        String u = uniqueName("dupe");
        expect(register(u, u + "@example.test", PASSWORD, "1999-01-01"), 201);
        expect(register(uniqueName("dupe2"), (u + "@EXAMPLE.test"), PASSWORD, "1999-01-01"), 409);
    }

    @Test
    void usersUnder18AreRejectedButExactly18IsAccepted() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String young = uniqueName("kid");
        Resp tooYoung = expect(register(young, young + "@example.test", PASSWORD, today.minusYears(17).toString()), 400);
        assertThat(tooYoung.text("detail")).contains("18");

        String adult = uniqueName("adult");
        expect(register(adult, adult + "@example.test", PASSWORD, today.minusYears(18).toString()), 201);
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() {
        String u = uniqueName("bad");
        Resp shortPassword = expect(register(u, u + "@example.test", "short", "1999-01-01"), 400);
        assertThat(shortPassword.body()).contains("password");
        expect(register("x", "not-an-email", PASSWORD, "1999-01-01"), 400);
        expect(register("has space", "a@example.test", PASSWORD, "1999-01-01"), 400);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameAnswer() {
        TestUser u = newUser("cred");
        Resp wrongPassword = expect(api.post("/api/auth/login", null, json("identifier", u.username(), "password", "WrongPass999")), 401);
        Resp unknownUser = expect(api.post("/api/auth/login", null, json("identifier", "it_nobody_" + System.nanoTime(), "password", PASSWORD)), 401);
        assertThat(wrongPassword.text("detail")).isEqualTo(unknownUser.text("detail"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() {
        expect(api.get("/api/users/me", null), 401);
        expect(api.get("/api/users/me", "not.a.jwt"), 401);
        expect(api.get("/api/feed", null), 401);
    }

    @Test
    void refreshTokensRotateAndReuseKillsTheWholeSession() {
        TestUser u = newUser("rot");
        Resp login = expect(api.post("/api/auth/login", null, json("identifier", u.username(), "password", PASSWORD)), 200);
        String first = login.text("refreshToken");

        Resp rotated = expect(api.post("/api/auth/refresh", null, json("refreshToken", first)), 200);
        String second = rotated.text("refreshToken");
        assertThat(second).isNotEqualTo(first);
        expect(api.get("/api/users/me", rotated.text("accessToken")), 200);

        // presenting the OLD token again looks like theft: it fails AND the newer token is revoked too
        expect(api.post("/api/auth/refresh", null, json("refreshToken", first)), 401);
        expect(api.post("/api/auth/refresh", null, json("refreshToken", second)), 401);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        TestUser u = newUser("out");
        Resp login = expect(api.post("/api/auth/login", null, json("identifier", u.username(), "password", PASSWORD)), 200);
        String refresh = login.text("refreshToken");

        expect(api.post("/api/auth/logout", null, json("refreshToken", refresh)), 204);
        expect(api.post("/api/auth/refresh", null, json("refreshToken", refresh)), 401);
        // logging out twice, or with junk, is not an error
        expect(api.post("/api/auth/logout", null, json("refreshToken", refresh)), 204);
        expect(api.post("/api/auth/logout", null, json("refreshToken", "junk")), 204);
    }

    @Test
    void suspendedAccountsCannotLoginOrRefresh() {
        TestUser u = newUser("susp");
        Resp login = expect(api.post("/api/auth/login", null, json("identifier", u.username(), "password", PASSWORD)), 200);

        db.sql("UPDATE users SET status = 'SUSPENDED' WHERE id = :id").param("id", u.id()).update();

        expect(api.post("/api/auth/login", null, json("identifier", u.username(), "password", PASSWORD)), 403);
        expect(api.post("/api/auth/refresh", null, json("refreshToken", login.text("refreshToken"))), 401);
    }
}
