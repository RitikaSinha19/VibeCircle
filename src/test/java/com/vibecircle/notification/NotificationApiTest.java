package com.vibecircle.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationApiTest extends ApiTestBase {

    private List<String> types(TestUser u) {
        return expect(api.get("/api/notifications?size=50", u.token()), 200).strings("items", "type");
    }

    private long count(TestUser u, String type) {
        return types(u).stream().filter(type::equals).count();
    }

    private long unread(TestUser u) {
        return expect(api.get("/api/notifications/unread-count", u.token()), 200).longValue("count");
    }

    @Test
    void likesNotifyTheAuthorOnceAndNeverNotifyYourselves() {
        TestUser author = newUser("auth"), fan = newUser("fan");
        PostRef post = createPost(author, "like me", "PUBLIC", false);

        expect(api.postEmpty("/api/posts/" + post.id() + "/like", author.token()), 200);   // own like
        assertThat(count(author, "POST_LIKED")).isZero();

        expect(api.postEmpty("/api/posts/" + post.id() + "/like", fan.token()), 200);
        expect(api.delete("/api/posts/" + post.id() + "/like", fan.token()), 200);
        expect(api.postEmpty("/api/posts/" + post.id() + "/like", fan.token()), 200);

        assertThat(count(author, "POST_LIKED")).isEqualTo(1);       // like, unlike, like again: still one
        assertThat(unread(author)).isEqualTo(1);
    }

    @Test
    void commentsFriendRequestsAndAcceptancesNotify() {
        TestUser author = newUser("auth"), other = newUser("oth");
        PostRef post = createPost(author, "talk to me", "PUBLIC", false);

        expect(api.post("/api/posts/" + post.id() + "/comments", other.token(), json("body", "hi")), 201);
        assertThat(count(author, "NEW_COMMENT")).isEqualTo(1);

        long reqId = expect(api.post("/api/friends/requests", other.token(), json("receiverId", author.id())), 201).longValue("requestId");
        assertThat(count(author, "FRIEND_REQUEST")).isEqualTo(1);

        expect(api.postEmpty("/api/friends/requests/" + reqId + "/accept", author.token()), 200);
        assertThat(count(other, "FRIEND_ACCEPTED")).isEqualTo(1);
    }

    @Test
    void notificationTextAndTargetAreReadable() {
        TestUser author = newUser("auth"), other = newUser("oth");
        PostRef post = createPost(author, "x", "PUBLIC", false);
        expect(api.postEmpty("/api/posts/" + post.id() + "/like", other.token()), 200);

        Resp list = expect(api.get("/api/notifications", author.token()), 200);
        assertThat(list.text("items", 0, "message")).isEqualTo(other.username() + " liked your post");
        assertThat(list.text("items", 0, "entityType")).isEqualTo("POST");
        assertThat(list.longValue("items", 0, "entityId")).isEqualTo(post.id());
    }

    @Test
    void switchedOffTypesAreNotCreated() {
        TestUser author = newUser("auth"), other = newUser("oth");
        PostRef post = createPost(author, "x", "PUBLIC", false);
        String comments = "/api/posts/" + post.id() + "/comments";

        expect(api.put("/api/notifications/preferences/NEW_COMMENT", author.token(), json("enabled", false)), 204);
        expect(api.post(comments, other.token(), json("body", "muted")), 201);
        assertThat(count(author, "NEW_COMMENT")).isZero();

        expect(api.put("/api/notifications/preferences/NEW_COMMENT", author.token(), json("enabled", true)), 204);
        expect(api.post(comments, other.token(), json("body", "audible")), 201);
        assertThat(count(author, "NEW_COMMENT")).isEqualTo(1);

        expect(api.put("/api/notifications/preferences/NOT_A_TYPE", author.token(), json("enabled", false)), 400);
    }

    @Test
    void markingReadDeletingAndOwnership() {
        TestUser author = newUser("auth"), other = newUser("oth");
        PostRef post = createPost(author, "x", "PUBLIC", false);
        expect(api.post("/api/posts/" + post.id() + "/comments", other.token(), json("body", "a")), 201);
        expect(api.postEmpty("/api/posts/" + post.id() + "/like", other.token()), 200);
        assertThat(unread(author)).isEqualTo(2);

        long id = expect(api.get("/api/notifications", author.token()), 200).longValue("items", 0, "id");
        expect(api.postEmpty("/api/notifications/" + id + "/read", other.token()), 404);     // not theirs
        expect(api.delete("/api/notifications/" + id, other.token()), 404);

        expect(api.postEmpty("/api/notifications/" + id + "/read", author.token()), 204);
        expect(api.postEmpty("/api/notifications/" + id + "/read", author.token()), 204);    // idempotent
        assertThat(unread(author)).isEqualTo(1);
        assertThat(expect(api.get("/api/notifications?unreadOnly=true", author.token()), 200).ids("items")).doesNotContain(id);

        expect(api.postEmpty("/api/notifications/read-all", author.token()), 200);
        assertThat(unread(author)).isZero();

        expect(api.delete("/api/notifications/" + id, author.token()), 204);
        expect(api.delete("/api/notifications/" + id, author.token()), 404);
    }
}
