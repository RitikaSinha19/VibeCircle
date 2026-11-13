package com.vibecircle.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import org.junit.jupiter.api.Test;

/**
 * The most important tests in the project: can anyone reach content they must not see?
 * Rule: author and friends see everything; everyone else sees a post only if it is PUBLIC
 * AND the author's profile is PUBLIC; blocked users see nothing; "no" is always a 404.
 */
class PrivacyApiTest extends ApiTestBase {

    private record Fixture(TestUser author, TestUser friend, TestUser stranger, TestUser blocked,
                           PostRef pub, PostRef priv) {
    }

    private Fixture fixture() {
        TestUser author = newUser("auth"), friend = newUser("frnd"), stranger = newUser("strg"), blocked = newUser("blkd");
        befriend(author, friend);
        PostRef pub = createPost(author, "public post", "PUBLIC", true);
        PostRef priv = createPost(author, "friends only post", "FRIENDS_ONLY", true);
        return new Fixture(author, friend, stranger, blocked, pub, priv);
    }

    private int post(TestUser viewer, PostRef p) {
        return api.get("/api/posts/" + p.id(), viewer.token()).status();
    }

    private int media(TestUser viewer, PostRef p) {
        return api.get("/api/media/" + p.mediaId(), viewer.token()).status();
    }

    @Test
    void strangersSeePublicPostsButNeverFriendsOnlyOnes() {
        Fixture f = fixture();
        assertThat(post(f.stranger(), f.pub())).isEqualTo(200);
        assertThat(post(f.stranger(), f.priv())).isEqualTo(404);
    }

    @Test
    void privateImagesAreProtectedToo() {
        Fixture f = fixture();
        assertThat(media(f.stranger(), f.pub())).isEqualTo(200);
        assertThat(media(f.stranger(), f.priv())).isEqualTo(404);
        assertThat(media(f.friend(), f.priv())).isEqualTo(200);
        assertThat(media(f.author(), f.priv())).isEqualTo(200);
    }

    @Test
    void friendsAndTheAuthorSeeEverything() {
        Fixture f = fixture();
        assertThat(post(f.friend(), f.priv())).isEqualTo(200);
        assertThat(post(f.friend(), f.pub())).isEqualTo(200);
        assertThat(post(f.author(), f.priv())).isEqualTo(200);
    }

    @Test
    void privatePostsNeverLeakIntoFeedsOrProfileListings() {
        Fixture f = fixture();

        assertThat(expect(api.get("/api/feed?size=50", f.stranger().token()), 200).ids("items"))
                .contains(f.pub().id()).doesNotContain(f.priv().id());
        assertThat(expect(api.get("/api/users/" + f.author().id() + "/posts?size=50", f.stranger().token()), 200).ids("items"))
                .contains(f.pub().id()).doesNotContain(f.priv().id());

        assertThat(expect(api.get("/api/feed?size=50", f.friend().token()), 200).ids("items"))
                .contains(f.pub().id(), f.priv().id());
        assertThat(expect(api.get("/api/users/" + f.author().id() + "/posts?size=50", f.friend().token()), 200).ids("items"))
                .contains(f.pub().id(), f.priv().id());
    }

    @Test
    void strangersCannotLikeOrCommentOnPrivatePosts() {
        Fixture f = fixture();
        String base = "/api/posts/" + f.priv().id();
        expect(api.postEmpty(base + "/like", f.stranger().token()), 404);
        expect(api.post(base + "/comments", f.stranger().token(), json("body", "let me in")), 404);
        expect(api.get(base + "/comments", f.stranger().token()), 404);

        expect(api.postEmpty(base + "/like", f.friend().token()), 200);      // a friend can
    }

    @Test
    void changingVisibilityTakesEffectImmediately() {
        Fixture f = fixture();
        String url = "/api/posts/" + f.priv().id();

        expect(api.patch(url, f.author().token(), json("audience", "PUBLIC")), 200);
        assertThat(post(f.stranger(), f.priv())).isEqualTo(200);
        assertThat(media(f.stranger(), f.priv())).isEqualTo(200);

        expect(api.patch(url, f.author().token(), json("audience", "FRIENDS_ONLY")), 200);
        assertThat(post(f.stranger(), f.priv())).isEqualTo(404);
        assertThat(media(f.stranger(), f.priv())).isEqualTo(404);
    }

    @Test
    void unfriendingRevokesAccessToFriendsOnlyPosts() {
        Fixture f = fixture();
        assertThat(post(f.friend(), f.priv())).isEqualTo(200);

        expect(api.delete("/api/friends/" + f.author().id(), f.friend().token()), 204);

        assertThat(post(f.friend(), f.priv())).isEqualTo(404);
        assertThat(media(f.friend(), f.priv())).isEqualTo(404);
    }

    @Test
    void aPrivateProfileHidesEvenItsPublicPostsFromStrangers() {
        Fixture f = fixture();
        expect(api.patch("/api/profiles/me", f.author().token(), json("bio", "my secret bio", "profileVisibility", "PRIVATE")), 200);

        assertThat(post(f.stranger(), f.pub())).isEqualTo(404);
        assertThat(media(f.stranger(), f.pub())).isEqualTo(404);
        assertThat(post(f.friend(), f.pub())).isEqualTo(200);

        Resp limited = expect(api.get("/api/users/" + f.author().id() + "/profile", f.stranger().token()), 200);
        assertThat(limited.text("access")).isEqualTo("LIMITED");
        assertThat(limited.value("bio")).isNull();
        assertThat(limited.value("interests")).isNull();

        Resp full = expect(api.get("/api/users/" + f.author().id() + "/profile", f.friend().token()), 200);
        assertThat(full.text("access")).isEqualTo("FULL");
        assertThat(full.text("bio")).isEqualTo("my secret bio");
    }

    @Test
    void blockedUsersCannotSeeOrTouchAnything() {
        Fixture f = fixture();
        expect(api.postEmpty("/api/blocks/" + f.blocked().id(), f.author().token()), 204);

        assertThat(post(f.blocked(), f.pub())).isEqualTo(404);
        assertThat(media(f.blocked(), f.pub())).isEqualTo(404);
        expect(api.get("/api/users/" + f.author().id() + "/profile", f.blocked().token()), 404);
        expect(api.get("/api/users/" + f.author().id() + "/posts", f.blocked().token()), 404);
        expect(api.postEmpty("/api/posts/" + f.pub().id() + "/like", f.blocked().token()), 404);
        expect(api.post("/api/posts/" + f.pub().id() + "/comments", f.blocked().token(), json("body", "hi")), 404);
        expect(api.post("/api/friends/requests", f.blocked().token(), json("receiverId", f.author().id())), 404);
        assertThat(expect(api.get("/api/feed?size=50", f.blocked().token()), 200).ids("items")).doesNotContain(f.pub().id());

        // the block is one person's doing: everybody else is unaffected
        assertThat(post(f.stranger(), f.pub())).isEqualTo(200);
    }

    @Test
    void deletedPostsDisappearEverywhere() {
        Fixture f = fixture();
        expect(api.delete("/api/posts/" + f.pub().id(), f.author().token()), 204);

        assertThat(post(f.stranger(), f.pub())).isEqualTo(404);
        assertThat(media(f.stranger(), f.pub())).isEqualTo(404);
        assertThat(media(f.author(), f.pub())).isEqualTo(404);
        assertThat(expect(api.get("/api/feed?size=50", f.stranger().token()), 200).ids("items")).doesNotContain(f.pub().id());
    }
}
