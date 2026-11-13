package com.vibecircle.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The PRD example: A (Bengaluru) has Cricket, Singing, Programming, Travelling.
 *   B shares Cricket + Programming -> union 5 -> 40%.   D is identical -> 100%.
 *   C is in another city, E shares nothing, F is private, G is blocked, H has a pending request, I is a friend.
 */
class DiscoveryApiTest extends ApiTestBase {

    private TestUser a, b, c, d, e, f, g, h, i;

    @BeforeEach
    void buildWorld() {
        // exact-list assertions: remove users left behind by the previous test in this class
        db.sql("DELETE FROM users WHERE username LIKE 'it\\_%'").update();

        a = newUser("a");
        b = newUser("b");
        c = newUser("c");
        d = newUser("d");
        e = newUser("e");
        f = newUser("f");
        g = newUser("g");
        h = newUser("h");
        i = newUser("i");

        setCityAndInterests(a, "Bengaluru", "Cricket", "Singing", "Programming", "Travelling");
        setCityAndInterests(b, "Bengaluru", "Cricket", "Programming", "Photography");
        setCityAndInterests(c, "Kolkata", "Cricket", "Singing", "Programming");
        setCityAndInterests(d, "Bengaluru", "Cricket", "Singing", "Programming", "Travelling");
        setCityAndInterests(e, "Bengaluru", "Gaming", "Anime");
        setCityAndInterests(f, "Bengaluru", "Cricket", "Programming");
        expect(api.patch("/api/profiles/me", f.token(), json("profileVisibility", "PRIVATE")), 200);
        setCityAndInterests(g, "Bengaluru", "Cricket", "Programming");
        setCityAndInterests(h, "Bengaluru", "Cricket", "Programming");
        setCityAndInterests(i, "Bengaluru", "Cricket", "Programming");

        expect(api.postEmpty("/api/blocks/" + g.id(), a.token()), 204);
        expect(api.post("/api/friends/requests", a.token(), json("receiverId", h.id())), 201);
        befriend(a, i);
    }

    private Resp discover(TestUser who, String query) {
        return expect(api.get("/api/discovery" + query, who.token()), 200);
    }

    private static Map<?, ?> item(Resp r, TestUser u) {
        for (Object o : (List<?>) r.value("items")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (u.username().equals(m.get("username"))) {
                return m;
            }
        }
        return null;
    }

    @Test
    void ranksSameCityUsersByJaccardSimilarity() {
        Resp r = discover(a, "?size=50");

        assertThat(r.strings("items", "username")).containsExactly(d.username(), b.username());
        assertThat(((Number) item(r, d).get("similarityPercent")).doubleValue()).isCloseTo(100.0, within(0.01));
        assertThat(((Number) item(r, b).get("similarityPercent")).doubleValue()).isCloseTo(40.0, within(0.01));
        assertThat(item(r, b).get("sharedInterests")).isEqualTo(List.of("Cricket", "Programming"));
        assertThat(((Number) item(r, b).get("sharedCount")).intValue()).isEqualTo(2);
    }

    @Test
    void neverRecommendsIneligibleUsers() {
        List<String> names = discover(a, "?size=50").strings("items", "username");
        assertThat(names).doesNotContain(
                a.username(),   // yourself
                c.username(),   // other city
                e.username(),   // no shared interest
                f.username(),   // private profile
                g.username(),   // blocked
                h.username(),   // pending request
                i.username());  // already a friend
    }

    @Test
    void blockedUsersDoNotSeeTheBlockerEither() {
        assertThat(discover(g, "?size=50").strings("items", "username")).doesNotContain(a.username());
    }

    @Test
    void hidingARecommendationRemovesItUntilUnhidden() {
        expect(api.postEmpty("/api/discovery/hidden/" + d.id(), a.token()), 204);
        assertThat(discover(a, "?size=50").strings("items", "username")).containsExactly(b.username());

        expect(api.delete("/api/discovery/hidden/" + d.id(), a.token()), 204);
        assertThat(discover(a, "?size=50").strings("items", "username")).containsExactly(d.username(), b.username());
    }

    @Test
    void searchByInterestAndBrowsingAnotherCity() {
        List<String> photographers = discover(a, "?interestId=" + interestId("Photography")).strings("items", "username");
        assertThat(photographers).containsExactly(b.username());

        List<String> kolkata = discover(a, "?cityId=" + cityId("Kolkata")).strings("items", "username");
        assertThat(kolkata).containsExactly(c.username());
    }

    @Test
    void usersWhoOptOutOfDiscoveryAreNotShown() {
        expect(api.patch("/api/profiles/me", b.token(), json("discoverable", false)), 200);
        assertThat(discover(a, "?size=50").strings("items", "username")).containsExactly(d.username());
    }

    @Test
    void usersWhoStopAcceptingRequestsAreNotShown() {
        expect(api.patch("/api/profiles/me", b.token(), json("friendRequestPolicy", "NOBODY")), 200);
        assertThat(discover(a, "?size=50").strings("items", "username")).containsExactly(d.username());
    }

    @Test
    void paginationReportsWhetherMoreResultsExist() {
        Resp first = discover(a, "?size=1");
        assertThat(first.strings("items", "username")).containsExactly(d.username());
        assertThat(first.value("hasNext")).isEqualTo(true);

        Resp second = discover(a, "?size=1&page=1");
        assertThat(second.strings("items", "username")).containsExactly(b.username());
        assertThat(second.value("hasNext")).isEqualTo(false);
    }

    @Test
    void discoveryNeedsACityAndInterests() {
        TestUser fresh = newUser("fresh");
        expect(api.get("/api/discovery", fresh.token()), 409);

        expect(api.patch("/api/profiles/me", fresh.token(), json("cityId", cityId("Bengaluru"))), 200);
        expect(api.get("/api/discovery", fresh.token()), 409);
    }
}
