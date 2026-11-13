package com.vibecircle.friendship;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.List;
import org.junit.jupiter.api.Test;

class FriendshipApiTest extends ApiTestBase {

    private Resp sendRequest(TestUser from, TestUser to) {
        return api.post("/api/friends/requests", from.token(), json("receiverId", to.id()));
    }

    private List<Long> friendIds(TestUser u) {
        return expect(api.get("/api/friends?size=50", u.token()), 200).ids("items", "userId");
    }

    private long friendshipRows(TestUser a, TestUser b) {
        return db.sql("SELECT count(*) FROM friendships WHERE user_low_id = :l AND user_high_id = :h")
                .param("l", Math.min(a.id(), b.id())).param("h", Math.max(a.id(), b.id()))
                .query(Long.class).single();
    }

    @Test
    void requestAcceptAndFriendLists() {
        TestUser a = newUser("a"), b = newUser("b");

        Resp sent = expect(sendRequest(a, b), 201);
        assertThat(sent.text("outcome")).isEqualTo("REQUEST_SENT");
        long reqId = sent.longValue("requestId");

        assertThat(expect(api.get("/api/friends/requests/received", b.token()), 200).ids("items")).contains(reqId);
        assertThat(expect(api.get("/api/friends/requests/sent", a.token()), 200).ids("items")).contains(reqId);
        expect(sendRequest(a, b), 409);                                   // already pending

        Resp accepted = expect(api.postEmpty("/api/friends/requests/" + reqId + "/accept", b.token()), 200);
        assertThat(accepted.text("username")).isEqualTo(a.username());

        assertThat(friendIds(a)).contains(b.id());
        assertThat(friendIds(b)).contains(a.id());
        expect(api.postEmpty("/api/friends/requests/" + reqId + "/accept", b.token()), 409);   // no longer pending
        expect(sendRequest(a, b), 409);                                                          // already friends
    }

    @Test
    void onlyTheReceiverCanAnswerARequest() {
        TestUser a = newUser("a"), b = newUser("b"), c = newUser("c");
        long reqId = expect(sendRequest(a, b), 201).longValue("requestId");

        expect(api.postEmpty("/api/friends/requests/" + reqId + "/accept", a.token()), 404);   // the sender cannot accept
        expect(api.postEmpty("/api/friends/requests/" + reqId + "/accept", c.token()), 404);   // a stranger cannot
        expect(api.postEmpty("/api/friends/requests/" + reqId + "/reject", c.token()), 404);
        assertThat(friendIds(a)).doesNotContain(b.id());
    }

    @Test
    void rejectedRequestsCanBeSentAgain() {
        TestUser a = newUser("a"), b = newUser("b");
        long first = expect(sendRequest(a, b), 201).longValue("requestId");
        expect(api.postEmpty("/api/friends/requests/" + first + "/reject", b.token()), 204);
        expect(sendRequest(a, b), 201);
    }

    @Test
    void senderCanCancelAPendingRequest() {
        TestUser a = newUser("a"), b = newUser("b");
        long reqId = expect(sendRequest(a, b), 201).longValue("requestId");

        expect(api.postEmpty("/api/friends/requests/" + reqId + "/cancel", a.token()), 204);
        assertThat(expect(api.get("/api/friends/requests/received", b.token()), 200).ids("items")).doesNotContain(reqId);
        expect(api.postEmpty("/api/friends/requests/" + reqId + "/accept", b.token()), 409);
        expect(api.postEmpty("/api/friends/requests/" + reqId + "/cancel", b.token()), 404);   // not b's request to cancel
    }

    @Test
    void cannotSendARequestToYourself() {
        TestUser a = newUser("a");
        expect(sendRequest(a, a), 400);
    }

    @Test
    void requestingSomeoneWhoAlreadyAskedYouMakesYouFriendsImmediately() {
        TestUser a = newUser("a"), b = newUser("b");
        expect(sendRequest(a, b), 201);
        Resp back = expect(sendRequest(b, a), 201);
        assertThat(back.text("outcome")).isEqualTo("FRIENDS");
        assertThat(friendIds(a)).contains(b.id());
        assertThat(friendIds(b)).contains(a.id());
        assertThat(friendshipRows(a, b)).isEqualTo(1);
    }

    @Test
    void usersCanSwitchOffFriendRequests() {
        TestUser a = newUser("a"), b = newUser("b");
        expect(api.patch("/api/profiles/me", b.token(), json("friendRequestPolicy", "NOBODY")), 200);
        expect(sendRequest(a, b), 403);
    }

    @Test
    void removingAFriendWorksOnce() {
        TestUser a = newUser("a"), b = newUser("b");
        befriend(a, b);
        expect(api.delete("/api/friends/" + b.id(), a.token()), 204);
        assertThat(friendIds(a)).doesNotContain(b.id());
        expect(api.delete("/api/friends/" + b.id(), a.token()), 404);
    }

    @Test
    void blockingEndsTheFriendshipAndCancelsPendingRequests() {
        TestUser a = newUser("a"), b = newUser("b"), c = newUser("c"), d = newUser("d");
        befriend(a, b);
        long pending = expect(sendRequest(c, d), 201).longValue("requestId");

        expect(api.postEmpty("/api/blocks/" + b.id(), a.token()), 204);
        expect(api.postEmpty("/api/blocks/" + c.id(), d.token()), 204);

        assertThat(friendIds(a)).doesNotContain(b.id());
        assertThat(friendIds(b)).doesNotContain(a.id());
        assertThat(expect(api.get("/api/friends/requests/received", d.token()), 200).ids("items")).doesNotContain(pending);
        expect(api.postEmpty("/api/friends/requests/" + pending + "/accept", d.token()), 409);

        // a block looks like "user not found" in both directions
        expect(sendRequest(b, a), 404);
        expect(sendRequest(a, b), 404);
        expect(sendRequest(c, d), 404);

        assertThat(expect(api.get("/api/blocks", a.token()), 200).ids("items", "userId")).contains(b.id());
    }

    @Test
    void unblockingDoesNotRestoreTheFriendship() {
        TestUser a = newUser("a"), b = newUser("b");
        befriend(a, b);
        expect(api.postEmpty("/api/blocks/" + b.id(), a.token()), 204);
        expect(api.delete("/api/blocks/" + b.id(), a.token()), 204);

        assertThat(friendIds(a)).doesNotContain(b.id());
        expect(sendRequest(a, b), 201);        // they have to become friends again
    }

    // ---------------------------------------------------------- concurrency
    @Test
    void tenSimultaneousAcceptsCreateExactlyOneFriendship() {
        TestUser a = newUser("a"), b = newUser("b");
        long reqId = expect(sendRequest(a, b), 201).longValue("requestId");

        List<Resp> results = runConcurrently(10,
                () -> api.postEmpty("/api/friends/requests/" + reqId + "/accept", b.token()));

        assertThat(countStatus(results, 200)).as("successful accepts").isEqualTo(1);
        assertThat(countStatus(results, 409)).as("rejected duplicates").isEqualTo(9);
        assertThat(friendshipRows(a, b)).isEqualTo(1);
    }

    @Test
    void eightSimultaneousRequestsCreateExactlyOnePendingRequest() {
        TestUser a = newUser("a"), b = newUser("b");

        List<Resp> results = runConcurrently(8, () -> sendRequest(a, b));

        assertThat(countStatus(results, 201)).as("created").isEqualTo(1);
        assertThat(countStatus(results, 409)).as("duplicates").isEqualTo(7);
        long pending = db.sql("SELECT count(*) FROM friend_requests WHERE sender_id = :a AND receiver_id = :b AND status = 'PENDING'")
                .param("a", a.id()).param("b", b.id()).query(Long.class).single();
        assertThat(pending).isEqualTo(1);
    }
}
