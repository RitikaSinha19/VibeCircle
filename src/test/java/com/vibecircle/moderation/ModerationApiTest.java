package com.vibecircle.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import org.junit.jupiter.api.Test;

class ModerationApiTest extends ApiTestBase {

    private Resp report(TestUser who, String type, long targetId, String reason) {
        return api.post("/api/reports", who.token(), json("targetType", type, "targetId", targetId, "reason", reason));
    }

    private Resp resolve(TestUser staff, long reportId, String action) {
        return api.post("/api/admin/reports/" + reportId + "/resolve", staff.token(), json("action", action, "resolution", "test"));
    }

    @Test
    void onlyStaffCanUseTheAdminEndpoints() {
        TestUser user = newUser("user");
        expect(api.get("/api/admin/reports", user.token()), 403);
        expect(api.get("/api/admin/reports/1", user.token()), 403);
        expect(api.postEmpty("/api/admin/reports/1/claim", user.token()), 403);
        expect(api.postEmpty("/api/admin/users/1/unsuspend", user.token()), 403);
        expect(api.get("/api/admin/reports", null), 401);

        TestUser mod = staff("mod", "MODERATOR");
        expect(api.get("/api/admin/reports", mod.token()), 200);
    }

    @Test
    void reportingRulesAreEnforced() {
        TestUser author = newUser("auth"), reporter = newUser("rep"), stranger = newUser("strg");
        PostRef pub = createPost(author, "public", "PUBLIC", false);
        PostRef hidden = createPost(author, "friends only", "FRIENDS_ONLY", false);

        expect(report(reporter, "POST", pub.id(), "SPAM"), 201);
        expect(report(reporter, "POST", pub.id(), "SPAM"), 409);                     // one open report each
        expect(report(author, "POST", pub.id(), "SPAM"), 400);                       // your own content
        expect(report(author, "USER", author.id(), "OTHER"), 400);                   // yourself
        expect(report(reporter, "POST", 999_999_999L, "SPAM"), 404);                 // does not exist
        expect(report(stranger, "POST", hidden.id(), "SPAM"), 404);                  // cannot see it, cannot report it
        expect(api.post("/api/reports", reporter.token(),
                json("targetType", "POST", "targetId", pub.id(), "reason", "BORING")), 400);
    }

    @Test
    void reportersSeeTheirOwnReportsOnly() {
        TestUser author = newUser("auth"), r1 = newUser("r1"), r2 = newUser("r2");
        PostRef post = createPost(author, "x", "PUBLIC", false);
        long id = expect(report(r1, "POST", post.id(), "SPAM"), 201).longValue("id");

        assertThat(expect(api.get("/api/reports/mine", r1.token()), 200).ids("items")).contains(id);
        assertThat(expect(api.get("/api/reports/mine", r2.token()), 200).ids("items")).doesNotContain(id);
    }

    @Test
    void removeAndSuspendClosesEveryReportAndEnforcesTheDecision() {
        TestUser spammer = newUser("spam"), r1 = newUser("r1"), r2 = newUser("r2");
        TestUser mod = staff("mod", "MODERATOR"), admin = staff("adm", "ADMIN");
        PostRef post = createPost(spammer, "BUY CHEAP WATCHES http://spam.example", "PUBLIC", false);

        long first = expect(report(r1, "POST", post.id(), "SPAM"), 201).longValue("id");
        expect(report(r2, "POST", post.id(), "SPAM"), 201);

        assertThat(expect(api.get("/api/admin/reports?size=50", mod.token()), 200).ids("items")).contains(first);
        Resp detail = expect(api.get("/api/admin/reports/" + first, mod.token()), 200);
        assertThat(detail.longValue("openReportsOnTarget")).isEqualTo(2);
        assertThat(detail.text("target", "summary")).contains("BUY CHEAP WATCHES");
        assertThat(detail.text("target", "ownerUsername")).isEqualTo(spammer.username());

        assertThat(expect(api.postEmpty("/api/admin/reports/" + first + "/claim", mod.token()), 200).text("status")).isEqualTo("UNDER_REVIEW");
        expect(api.postEmpty("/api/admin/reports/" + first + "/claim", mod.token()), 409);

        assertThat(expect(resolve(mod, first, "REMOVE_AND_SUSPEND"), 200).text("status")).isEqualTo("ACTIONED");

        // the post is gone and the account is locked out
        expect(api.get("/api/posts/" + post.id(), r1.token()), 404);
        expect(api.post("/api/auth/login", null, json("identifier", spammer.username(), "password", PASSWORD)), 403);
        // both reporters' reports were closed together
        assertThat(expect(api.get("/api/reports/mine", r2.token()), 200).text("items", 0, "status")).isEqualTo("ACTIONED");
        expect(resolve(mod, first, "DISMISS"), 409);                                  // cannot resolve twice

        // only an admin can lift a suspension
        expect(api.postEmpty("/api/admin/users/" + spammer.id() + "/unsuspend", mod.token()), 403);
        expect(api.postEmpty("/api/admin/users/" + spammer.id() + "/unsuspend", admin.token()), 204);
        expect(api.post("/api/auth/login", null, json("identifier", spammer.username(), "password", PASSWORD)), 200);

        long audited = db.sql("SELECT count(*) FROM moderation_actions WHERE target_type = 'POST' AND target_id = :id")
                .param("id", post.id()).query(Long.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void dismissingChangesNothing() {
        TestUser author = newUser("auth"), reporter = newUser("rep");
        TestUser mod = staff("mod", "MODERATOR");
        PostRef post = createPost(author, "perfectly fine", "PUBLIC", false);
        long id = expect(report(reporter, "POST", post.id(), "OTHER"), 201).longValue("id");

        assertThat(expect(resolve(mod, id, "DISMISS"), 200).text("status")).isEqualTo("DISMISSED");
        expect(api.get("/api/posts/" + post.id(), reporter.token()), 200);
    }

    @Test
    void staffCannotBeSuspendedAndUsersCannotBeRemovedAsContent() {
        TestUser reporter = newUser("rep"), victim = newUser("vic");
        TestUser mod = staff("mod", "MODERATOR"), otherMod = staff("mod2", "MODERATOR");

        long staffReport = expect(report(reporter, "USER", otherMod.id(), "OTHER"), 201).longValue("id");
        expect(resolve(mod, staffReport, "SUSPEND_USER"), 403);

        long userReport = expect(report(reporter, "USER", victim.id(), "OTHER"), 201).longValue("id");
        expect(resolve(mod, userReport, "REMOVE_CONTENT"), 400);
        assertThat(expect(resolve(mod, userReport, "SUSPEND_USER"), 200).text("status")).isEqualTo("ACTIONED");
    }

    @Test
    void aReportedChatMessageBecomesATombstoneForBothSides() {
        TestUser a = newUser("a"), b = newUser("b");
        TestUser mod = staff("mod", "MODERATOR");
        befriend(a, b);
        long conv = expect(api.post("/api/chat/conversations", a.token(), json("userId", b.id())), 200).longValue("id");
        long msg = expect(api.post("/api/chat/conversations/" + conv + "/messages", b.token(), json("text", "something abusive")), 201).longValue("id");

        long id = expect(report(a, "MESSAGE", msg, "HARASSMENT"), 201).longValue("id");
        expect(resolve(mod, id, "REMOVE_CONTENT"), 200);

        Resp view = expect(api.get("/api/chat/conversations/" + conv + "/messages", a.token()), 200);
        assertThat(view.value("items", 0, "unsent")).isEqualTo(true);
        assertThat(view.value("items", 0, "text")).isNull();
    }

    @Test
    void outsidersCannotReportAPrivateChatMessage() {
        TestUser a = newUser("a"), b = newUser("b"), outsider = newUser("out");
        befriend(a, b);
        long conv = expect(api.post("/api/chat/conversations", a.token(), json("userId", b.id())), 200).longValue("id");
        long msg = expect(api.post("/api/chat/conversations/" + conv + "/messages", a.token(), json("text", "private")), 201).longValue("id");

        expect(report(outsider, "MESSAGE", msg, "OTHER"), 404);
    }
}
