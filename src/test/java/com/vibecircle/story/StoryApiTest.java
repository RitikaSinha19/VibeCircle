package com.vibecircle.story;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Multipart;
import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoryApiTest extends ApiTestBase {

    @Autowired
    private StoryCleanupJob cleanupJob;

    private long story(TestUser author, String text, String audience, boolean withImage) {
        Multipart m = new Multipart().field("audience", audience);
        if (text != null) {
            m.field("text", text);
        }
        if (withImage) {
            m.file("file", "pic.png", "image/png", PNG);
        }
        return expect(api.multipart("/api/stories", author.token(), m), 201).longValue("id");
    }

    private int status(TestUser viewer, String path) {
        return api.get(path, viewer.token()).status();
    }

    private List<Long> storiesOf(TestUser viewer, TestUser author) {
        Resp r = expect(api.get("/api/users/" + author.id() + "/stories", viewer.token()), 200);
        return r.jsonList().stream().map(o -> ((Number) ((Map<?, ?>) o).get("id")).longValue()).toList();
    }

    private Map<?, ?> trayItem(TestUser viewer, TestUser author) {
        Resp r = expect(api.get("/api/stories/tray?size=50", viewer.token()), 200);
        for (Object o : (List<?>) r.value("items")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (author.username().equals(((Map<?, ?>) m.get("author")).get("username"))) {
                return m;
            }
        }
        return null;
    }

    private long count(String sql, long id) {
        return db.sql(sql).param("id", id).query(Long.class).single();
    }

    @Test
    void aStoryLastsTwentyFourHours() {
        TestUser a = newUser("a");
        long id = story(a, "hello", "FRIENDS_ONLY", false);
        long seconds = count("SELECT CAST(extract(epoch FROM (expires_at - created_at)) AS bigint) FROM stories WHERE id = :id", id);
        assertThat(seconds).isEqualTo(24 * 3600);
    }

    @Test
    void friendsOnlyStoriesAreVisibleToFriendsAndNeverToStrangers() {
        TestUser a = newUser("a"), friend = newUser("frnd"), stranger = newUser("strg");
        befriend(a, friend);
        long id = story(a, "friends only", "FRIENDS_ONLY", true);

        assertThat(status(friend, "/api/stories/" + id)).isEqualTo(200);
        assertThat(status(friend, "/api/stories/" + id + "/media")).isEqualTo(200);
        assertThat(storiesOf(friend, a)).contains(id);
        assertThat(trayItem(friend, a)).isNotNull();

        assertThat(status(stranger, "/api/stories/" + id)).isEqualTo(404);
        assertThat(status(stranger, "/api/stories/" + id + "/media")).isEqualTo(404);
        expect(api.postEmpty("/api/stories/" + id + "/view", stranger.token()), 404);
        assertThat(storiesOf(stranger, a)).doesNotContain(id);
        assertThat(trayItem(stranger, a)).isNull();
    }

    @Test
    void publicStoriesAreReachableByStrangersThroughTheProfileButNotTheirTray() {
        TestUser a = newUser("a"), stranger = newUser("strg");
        long id = story(a, "for everyone", "PUBLIC", true);

        assertThat(status(stranger, "/api/stories/" + id)).isEqualTo(200);
        assertThat(status(stranger, "/api/stories/" + id + "/media")).isEqualTo(200);
        assertThat(storiesOf(stranger, a)).contains(id);
        assertThat(trayItem(stranger, a)).isNull();           // the tray is only me + friends
    }

    @Test
    void aPrivateProfileHidesEvenItsPublicStories() {
        TestUser a = newUser("a"), friend = newUser("frnd"), stranger = newUser("strg");
        befriend(a, friend);
        long id = story(a, "public but private profile", "PUBLIC", false);
        expect(api.patch("/api/profiles/me", a.token(), json("profileVisibility", "PRIVATE")), 200);

        assertThat(status(stranger, "/api/stories/" + id)).isEqualTo(404);
        assertThat(storiesOf(stranger, a)).doesNotContain(id);
        assertThat(status(friend, "/api/stories/" + id)).isEqualTo(200);
    }

    @Test
    void blockedUsersCannotSeeAnything() {
        TestUser a = newUser("a"), blocked = newUser("blkd"), other = newUser("oth");
        long id = story(a, "public", "PUBLIC", true);
        expect(api.postEmpty("/api/blocks/" + blocked.id(), a.token()), 204);

        assertThat(status(blocked, "/api/stories/" + id)).isEqualTo(404);
        assertThat(status(blocked, "/api/stories/" + id + "/media")).isEqualTo(404);
        expect(api.get("/api/users/" + a.id() + "/stories", blocked.token()), 404);
        assertThat(status(other, "/api/stories/" + id)).isEqualTo(200);        // unaffected
    }

    @Test
    void viewsAreCountedOncePerViewerAndOnlyTheAuthorSeesWhoWatched() {
        TestUser a = newUser("a"), f1 = newUser("f1"), f2 = newUser("f2");
        befriend(a, f1);
        befriend(a, f2);
        long id = story(a, "watch me", "FRIENDS_ONLY", false);

        expect(api.postEmpty("/api/stories/" + id + "/view", f1.token()), 204);
        expect(api.postEmpty("/api/stories/" + id + "/view", f1.token()), 204);    // idempotent
        expect(api.postEmpty("/api/stories/" + id + "/view", f2.token()), 204);
        expect(api.postEmpty("/api/stories/" + id + "/view", a.token()), 204);     // the author does not count

        Resp mine = expect(api.get("/api/stories/mine", a.token()), 200);
        assertThat(((Number) ((Map<?, ?>) mine.jsonList().get(0)).get("viewCount")).longValue()).isEqualTo(2);

        Resp viewers = expect(api.get("/api/stories/" + id + "/viewers", a.token()), 200);
        assertThat(viewers.strings("items", "username")).containsExactlyInAnyOrder(f1.username(), f2.username());

        // nobody else learns who watched, or how many
        expect(api.get("/api/stories/" + id + "/viewers", f1.token()), 404);
        Resp asFriend = expect(api.get("/api/stories/" + id, f1.token()), 200);
        assertThat(asFriend.value("viewCount")).isNull();
        assertThat(asFriend.value("viewedByMe")).isEqualTo(true);
    }

    @Test
    void theTrayMarksUnseenStoriesAndListsMyOwnFirst() {
        TestUser a = newUser("a"), friend = newUser("frnd");
        befriend(a, friend);
        long mine = story(friend, "my own story", "FRIENDS_ONLY", false);
        long theirs = story(a, "a's story", "FRIENDS_ONLY", false);

        Resp before = expect(api.get("/api/stories/tray", friend.token()), 200);
        assertThat(before.text("items", 0, "author", "username")).isEqualTo(friend.username());   // my own first
        assertThat(trayItem(friend, a).get("hasUnseen")).isEqualTo(true);

        expect(api.postEmpty("/api/stories/" + theirs + "/view", friend.token()), 204);
        assertThat(trayItem(friend, a).get("hasUnseen")).isEqualTo(false);
        assertThat(trayItem(friend, friend).get("hasUnseen")).isEqualTo(false);    // own story is never "unseen"
        assertThat(mine).isNotEqualTo(theirs);
    }

    @Test
    void deletedStoriesDisappearImmediatelyEverywhere() {
        TestUser a = newUser("a"), friend = newUser("frnd");
        befriend(a, friend);
        long id = story(a, "oops", "FRIENDS_ONLY", true);

        expect(api.delete("/api/stories/" + id, friend.token()), 404);     // only the author can delete
        expect(api.delete("/api/stories/" + id, a.token()), 204);
        expect(api.delete("/api/stories/" + id, a.token()), 404);

        assertThat(status(friend, "/api/stories/" + id)).isEqualTo(404);
        assertThat(status(friend, "/api/stories/" + id + "/media")).isEqualTo(404);
        assertThat(status(a, "/api/stories/" + id)).isEqualTo(404);
        assertThat(trayItem(friend, a)).isNull();
    }

    @Test
    void expiredStoriesAreInvisibleEvenBeforeCleanupRuns() {
        TestUser a = newUser("a"), friend = newUser("frnd");
        befriend(a, friend);
        long id = story(a, "short lived", "FRIENDS_ONLY", true);

        db.sql("UPDATE stories SET created_at = now() - interval '25 hours', expires_at = now() - interval '1 minute' WHERE id = :id")
                .param("id", id).update();

        assertThat(status(friend, "/api/stories/" + id)).isEqualTo(404);
        assertThat(status(friend, "/api/stories/" + id + "/media")).isEqualTo(404);
        assertThat(status(a, "/api/stories/" + id)).isEqualTo(404);
        assertThat(trayItem(friend, a)).isNull();
        assertThat(expect(api.get("/api/stories/mine", a.token()), 200).jsonList()).isEmpty();
        expect(api.delete("/api/stories/" + id, a.token()), 404);          // an expired story cannot be "deleted" either
    }

    @Test
    void cleanupRemovesOldExpiredAndDeletedStoriesButKeepsEverythingElse() {
        TestUser a = newUser("a");
        long live = story(a, "still live", "PUBLIC", true);
        long justExpired = story(a, "expired a minute ago", "PUBLIC", true);
        long longExpired = story(a, "expired long ago", "PUBLIC", true);
        long deletedLongAgo = story(a, "deleted long ago", "PUBLIC", false);

        db.sql("UPDATE stories SET created_at = now() - interval '25 hours', expires_at = now() - interval '1 minute' WHERE id = :id")
                .param("id", justExpired).update();                       // inside the 1-hour retention window
        db.sql("UPDATE stories SET created_at = now() - interval '30 hours', expires_at = now() - interval '6 hours' WHERE id = :id")
                .param("id", longExpired).update();
        expect(api.delete("/api/stories/" + deletedLongAgo, a.token()), 204);
        db.sql("UPDATE stories SET deleted_at = now() - interval '3 hours' WHERE id = :id").param("id", deletedLongAgo).update();

        int removed = cleanupJob.run();

        assertThat(removed).isGreaterThanOrEqualTo(2);
        assertThat(count("SELECT count(*) FROM stories WHERE id = :id", longExpired)).isZero();
        assertThat(count("SELECT count(*) FROM stories WHERE id = :id", deletedLongAgo)).isZero();
        assertThat(count("SELECT count(*) FROM stories WHERE id = :id", live)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM stories WHERE id = :id", justExpired)).isEqualTo(1);
        assertThat(status(a, "/api/stories/" + live + "/media")).isEqualTo(200);    // the live story's file is untouched
    }

    @Test
    void invalidStoriesAreRejected() {
        TestUser a = newUser("a");
        expect(api.multipart("/api/stories", a.token(), new Multipart().field("audience", "PUBLIC")), 400);
        expect(api.multipart("/api/stories", a.token(), new Multipart().field("text", "   ")), 400);
        expect(api.multipart("/api/stories", a.token(), new Multipart().field("text", "x".repeat(281))), 400);
        expect(api.multipart("/api/stories", a.token(), new Multipart().field("text", "hi").field("audience", "EVERYONE")), 400);
        expect(api.multipart("/api/stories", a.token(),
                new Multipart().file("file", "fake.png", "image/png", "not an image".getBytes())), 400);
    }

    @Test
    void storiesDefaultToFriendsOnly() {
        TestUser a = newUser("a"), stranger = newUser("strg");
        long id = expect(api.multipart("/api/stories", a.token(), new Multipart().field("text", "no audience given")), 201)
                .longValue("id");
        assertThat(db.sql("SELECT audience FROM stories WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("FRIENDS_ONLY");
        assertThat(status(stranger, "/api/stories/" + id)).isEqualTo(404);
    }
}
