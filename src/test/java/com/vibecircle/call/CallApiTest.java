package com.vibecircle.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The tests run with vibecircle.calls.require-presence=false (see application-test.properties) because plain HTTP
 * tests have no WebSocket session. The "callee offline" rule is covered by CallServiceTest instead.
 */
class CallApiTest extends ApiTestBase {

    @Autowired
    private CallJanitor janitor;

    private TestUser a, b, c;

    @BeforeEach
    void setUp() {
        a = newUser("a");
        b = newUser("b");
        c = newUser("c");
        befriend(a, b);
        befriend(a, c);
        befriend(b, c);        // the busy-line tests need all three to be friends
    }

    private Resp start(TestUser from, TestUser to, String type) {
        return api.post("/api/calls", from.token(), json("calleeId", to.id(), "type", type));
    }

    private long startOk(TestUser from, TestUser to, String type) {
        return expect(start(from, to, type), 201).longValue("id");
    }

    private Resp act(TestUser who, long callId, String action) {
        return api.postEmpty("/api/calls/" + callId + "/" + action, who.token());
    }

    private String statusInDb(long callId) {
        return db.sql("SELECT status FROM calls WHERE id = :id").param("id", callId).query(String.class).single();
    }

    private long openCallsOf(TestUser u) {
        return db.sql("SELECT count(*) FROM calls WHERE (caller_id = :u OR callee_id = :u) AND status IN ('RINGING','ACTIVE')")
                .param("u", u.id()).query(Long.class).single();
    }

    // ------------------------------------------------------------------ rules
    @Test
    void onlyFriendsCanBeCalled() {
        TestUser stranger = newUser("strg");
        expect(start(a, stranger, "VOICE"), 403);
        expect(start(a, a, "VOICE"), 400);
        expect(api.post("/api/calls", a.token(), json("calleeId", b.id(), "type", "HOLOGRAM")), 400);
        expect(api.post("/api/calls", a.token(), "{}"), 400);
    }

    @Test
    void aFullCallFromRingingToHangUp() {
        Resp started = expect(start(a, b, "VIDEO"), 201);
        long id = started.longValue("id");
        assertThat(started.text("status")).isEqualTo("RINGING");
        assertThat(started.text("type")).isEqualTo("VIDEO");
        assertThat(started.text("caller", "username")).isEqualTo(a.username());
        assertThat(started.text("callee", "username")).isEqualTo(b.username());
        assertThat(started.value("answeredAt")).isNull();

        expect(api.get("/api/calls/" + id, b.token()), 200);

        Resp accepted = expect(act(b, id, "accept"), 200);
        assertThat(accepted.text("status")).isEqualTo("ACTIVE");
        assertThat(accepted.value("answeredAt")).isNotNull();

        Resp ended = expect(act(a, id, "end"), 200);
        assertThat(ended.text("status")).isEqualTo("ENDED");
        assertThat(ended.value("endedAt")).isNotNull();
        assertThat(((Number) ended.value("durationSeconds")).longValue()).isGreaterThanOrEqualTo(0);

        assertThat(expect(api.get("/api/calls", a.token()), 200).ids("items")).contains(id);
        assertThat(expect(api.get("/api/calls", b.token()), 200).ids("items")).contains(id);
    }

    @Test
    void theCalleeCanRejectAndTheCallerCanCancel() {
        long rejected = startOk(a, b, "VOICE");
        assertThat(expect(act(b, rejected, "reject"), 200).text("status")).isEqualTo("REJECTED");

        long cancelled = startOk(a, b, "VOICE");
        assertThat(expect(act(a, cancelled, "cancel"), 200).text("status")).isEqualTo("CANCELLED");
    }

    @Test
    void eitherSideCanReportAFailure() {
        long id = startOk(a, b, "VIDEO");
        expect(act(b, id, "accept"), 200);
        assertThat(expect(act(b, id, "failed"), 200).text("status")).isEqualTo("FAILED");
        assertThat(openCallsOf(a)).isZero();
    }

    @Test
    void onlyTheRightPartyCanChangeACall() {
        long id = startOk(a, b, "VOICE");

        expect(act(a, id, "accept"), 404);          // the caller cannot answer their own call
        expect(act(b, id, "cancel"), 404);          // the callee cannot "cancel" (they reject)
        expect(act(c, id, "accept"), 404);          // a third person cannot touch it
        expect(act(c, id, "reject"), 404);
        expect(act(c, id, "end"), 404);
        expect(api.get("/api/calls/" + id, c.token()), 404);
        expect(act(a, id, "end"), 409);             // not answered yet, so there is nothing to end

        expect(act(b, id, "accept"), 200);
        expect(act(b, id, "accept"), 409);          // already answered
        expect(act(a, id, "cancel"), 409);          // too late to cancel
        expect(act(b, id, "end"), 200);
        expect(act(a, id, "end"), 409);             // closed calls are final
    }

    @Test
    void aUserIsInAtMostOneCallAtATime() {
        long id = startOk(a, b, "VOICE");

        expect(start(c, b, "VOICE"), 409);          // b is ringing: busy
        expect(start(a, c, "VOICE"), 409);          // a is already calling someone
        expect(start(b, c, "VOICE"), 409);          // b has an incoming call open

        expect(act(b, id, "reject"), 200);
        startOk(c, b, "VOICE");                     // free again
    }

    // ------------------------------------------------------------ concurrency
    @Test
    void simultaneousCallsBetweenTheSamePairCreateExactlyOneOpenCall() {
        AtomicInteger n = new AtomicInteger();
        List<Resp> results = runConcurrently(2,
                () -> n.getAndIncrement() == 0 ? start(a, b, "VOICE") : start(b, a, "VOICE"));

        assertThat(countStatus(results, 201)).isEqualTo(1);
        assertThat(countStatus(results, 409)).isEqualTo(1);
        assertThat(openCallsOf(a)).isEqualTo(1);
        assertThat(openCallsOf(b)).isEqualTo(1);
    }

    @Test
    void eightSimultaneousStartsCreateExactlyOneCall() {
        List<Resp> results = runConcurrently(8, () -> start(a, b, "VIDEO"));

        assertThat(countStatus(results, 201)).isEqualTo(1);
        assertThat(countStatus(results, 409)).isEqualTo(7);
        assertThat(openCallsOf(a)).isEqualTo(1);
    }

    @Test
    void aPersonCalledByTwoFriendsAtOnceRingsForExactlyOne() {
        AtomicInteger n = new AtomicInteger();
        List<Resp> results = runConcurrently(2, () -> n.getAndIncrement() == 0 ? start(a, b, "VOICE") : start(c, b, "VOICE"));

        assertThat(countStatus(results, 201)).isEqualTo(1);
        assertThat(openCallsOf(b)).isEqualTo(1);
    }

    @Test
    void fiveSimultaneousAnswersActivateTheCallOnce() {
        long id = startOk(a, b, "VOICE");

        List<Resp> results = runConcurrently(5, () -> act(b, id, "accept"));

        assertThat(countStatus(results, 200)).isEqualTo(1);
        assertThat(countStatus(results, 409)).isEqualTo(4);
        assertThat(statusInDb(id)).isEqualTo("ACTIVE");
    }

    // ----------------------------------------------------------------- janitor
    @Test
    void unansweredCallsBecomeMissedAndTheCalleeIsNotified() {
        long id = startOk(a, b, "VOICE");
        db.sql("UPDATE calls SET created_at = now() - interval '5 minutes' WHERE id = :id").param("id", id).update();

        assertThat(janitor.runOnce()).isGreaterThanOrEqualTo(1);

        assertThat(statusInDb(id)).isEqualTo("MISSED");
        assertThat(expect(api.get("/api/notifications?size=50", b.token()), 200).strings("items", "type")).contains("MISSED_CALL");
        assertThat(expect(api.get("/api/notifications?size=50", a.token()), 200).strings("items", "type")).doesNotContain("MISSED_CALL");
        expect(act(b, id, "accept"), 409);          // too late
        startOk(a, b, "VOICE");                     // and nobody is stuck "in a call"
    }

    @Test
    void callsThatRunTooLongAreEnded() {
        long id = startOk(a, b, "VIDEO");
        expect(act(b, id, "accept"), 200);
        db.sql("UPDATE calls SET answered_at = now() - interval '3 hours' WHERE id = :id").param("id", id).update();

        assertThat(janitor.runOnce()).isGreaterThanOrEqualTo(1);

        assertThat(statusInDb(id)).isEqualTo("ENDED");
        Resp view = expect(api.get("/api/calls/" + id, a.token()), 200);
        assertThat(((Number) view.value("durationSeconds")).longValue()).isGreaterThan(2 * 3600);
    }

    @Test
    void blockingSomeoneEndsTheCallImmediatelyAndPreventsNewOnes() {
        long id = startOk(a, b, "VOICE");
        expect(act(b, id, "accept"), 200);

        expect(api.postEmpty("/api/blocks/" + b.id(), a.token()), 204);
        assertThat(janitor.runOnce()).isGreaterThanOrEqualTo(1);

        assertThat(statusInDb(id)).isEqualTo("ENDED");
        assertThat(openCallsOf(a)).isZero();
        expect(start(a, b, "VOICE"), 403);
        expect(start(b, a, "VOICE"), 403);
    }

    @Test
    void unfriendingCancelsARingingCall() {
        long id = startOk(a, b, "VOICE");
        expect(api.delete("/api/friends/" + b.id(), a.token()), 204);

        janitor.runOnce();

        assertThat(statusInDb(id)).isEqualTo("CANCELLED");
    }

    @Test
    void youCannotAnswerAfterBeingUnfriended() {
        long id = startOk(a, b, "VOICE");
        expect(api.delete("/api/friends/" + b.id(), a.token()), 204);

        expect(act(b, id, "accept"), 403);
    }

    // ------------------------------------------------------------- ICE servers
    @Test
    void iceServersAreOnlyIssuedWhileInACall() {
        expect(api.get("/api/calls/ice-servers", a.token()), 409);

        long id = startOk(a, b, "VOICE");
        Resp ice = expect(api.get("/api/calls/ice-servers", a.token()), 200);
        assertThat(ice.text("iceServers", 0, "urls", 0)).startsWith("stun:");
        assertThat(expect(api.get("/api/calls/ice-servers", b.token()), 200).value("iceServers")).isNotNull();   // callee too
        expect(api.get("/api/calls/ice-servers", c.token()), 409);                                              // a bystander: no

        expect(act(b, id, "reject"), 200);
        expect(api.get("/api/calls/ice-servers", a.token()), 409);
    }

    // ----------------------------------------------------------------- history
    @Test
    void historyIsPagedAndPrivate() {
        for (int i = 0; i < 3; i++) {
            long id = startOk(a, b, "VOICE");
            expect(act(b, id, "reject"), 200);
        }
        Resp page = expect(api.get("/api/calls?size=2", a.token()), 200);
        assertThat(((List<?>) page.value("items"))).hasSize(2);
        assertThat(page.value("hasNext")).isEqualTo(true);

        assertThat(((List<?>) expect(api.get("/api/calls", c.token()), 200).value("items"))).isEmpty();
    }
}
