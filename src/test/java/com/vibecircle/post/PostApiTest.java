package com.vibecircle.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Multipart;
import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostApiTest extends ApiTestBase {

    @Test
    void aPostNeedsACaptionOrAFile() {
        TestUser u = newUser("p");
        expect(api.multipart("/api/posts", u.token(), new Multipart().field("audience", "PUBLIC")), 400);
        expect(api.multipart("/api/posts", u.token(), new Multipart().field("caption", "   ").field("audience", "PUBLIC")), 400);
        expect(api.multipart("/api/posts", u.token(), new Multipart().field("caption", "x".repeat(2201))), 400);
        expect(api.multipart("/api/posts", u.token(), new Multipart().field("caption", "hi").field("audience", "EVERYONE")), 400);
        expect(api.multipart("/api/posts", u.token(), new Multipart().field("caption", "hi").field("cityId", "999999")), 400);
    }

    @Test
    void uploadsAreCheckedByContentNotByFileName() {
        TestUser u = newUser("p");
        Multipart fake = new Multipart().field("caption", "x")
                .file("files", "totally.png", "image/png", "this is plain text".getBytes());
        expect(api.multipart("/api/posts", u.token(), fake), 400);

        PostRef ok = createPost(u, "real image", "PUBLIC", true);
        assertThat(ok.mediaId()).isNotNull();
    }

    @Test
    void hashtagsAreExtractedLowercasedAndDeduplicated() {
        TestUser u = newUser("p");
        Resp r = expect(api.multipart("/api/posts", u.token(),
                new Multipart().field("caption", "Great #Cricket day #cricket #Code_1").field("audience", "PUBLIC")), 201);
        assertThat(r.value("hashtags")).isEqualTo(List.of("code_1", "cricket"));
    }

    @Test
    void onlyTheAuthorCanEditOrDelete() {
        TestUser author = newUser("auth"), other = newUser("oth");
        PostRef post = createPost(author, "original #one", "PUBLIC", false);

        expect(api.patch("/api/posts/" + post.id(), other.token(), json("caption", "hijacked")), 404);
        expect(api.delete("/api/posts/" + post.id(), other.token()), 404);

        Resp edited = expect(api.patch("/api/posts/" + post.id(), author.token(), json("caption", "changed #two")), 200);
        assertThat(edited.text("caption")).isEqualTo("changed #two");
        assertThat(edited.value("hashtags")).isEqualTo(List.of("two"));
        expect(api.patch("/api/posts/" + post.id(), author.token(), "{}"), 400);

        expect(api.delete("/api/posts/" + post.id(), author.token()), 204);
        expect(api.get("/api/posts/" + post.id(), author.token()), 404);
    }

    @Test
    void likingIsIdempotent() {
        TestUser author = newUser("auth"), fan = newUser("fan");
        PostRef post = createPost(author, "like me", "PUBLIC", false);

        assertThat(expect(api.postEmpty("/api/posts/" + post.id() + "/like", fan.token()), 200).longValue("likeCount")).isEqualTo(1);
        assertThat(expect(api.postEmpty("/api/posts/" + post.id() + "/like", fan.token()), 200).longValue("likeCount")).isEqualTo(1);
        assertThat(expect(api.delete("/api/posts/" + post.id() + "/like", fan.token()), 200).longValue("likeCount")).isEqualTo(0);
    }

    @Test
    void twentySimultaneousLikesFromOneUserCountOnce() {
        TestUser author = newUser("auth"), fan = newUser("fan");
        PostRef post = createPost(author, "race", "PUBLIC", false);

        List<Resp> results = runConcurrently(20, () -> api.postEmpty("/api/posts/" + post.id() + "/like", fan.token()));

        assertThat(countStatus(results, 200)).isEqualTo(20);
        long likes = db.sql("SELECT count(*) FROM post_likes WHERE post_id = :p").param("p", post.id()).query(Long.class).single();
        assertThat(likes).isEqualTo(1);
    }

    @Test
    void commentsCanBeAddedListedAndDeletedByTheirOwnerOnly() {
        TestUser author = newUser("auth"), a = newUser("a"), b = newUser("b");
        PostRef post = createPost(author, "discuss", "PUBLIC", false);
        String base = "/api/posts/" + post.id() + "/comments";

        long commentId = expect(api.post(base, a.token(), json("body", "first!")), 201).longValue("id");
        expect(api.post(base, a.token(), json("body", "   ")), 400);
        expect(api.post(base, a.token(), json("body", "x".repeat(1001))), 400);

        assertThat(expect(api.get(base, b.token()), 200).ids("items")).contains(commentId);
        expect(api.delete(base + "/" + commentId, b.token()), 404);        // not b's comment
        expect(api.delete(base + "/" + commentId, author.token()), 404);   // PRD: delete OWN comments only
        expect(api.delete(base + "/" + commentId, a.token()), 204);
        assertThat(expect(api.get(base, b.token()), 200).ids("items")).doesNotContain(commentId);
    }

    @Test
    void cursorPagingWalksEveryPostExactlyOnceNewestFirst() {
        TestUser u = newUser("pager");
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(createPost(u, "post " + i, "PUBLIC", false).id());
        }

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 10; guard++) {
            Resp page = expect(api.get("/api/feed?scope=FRIENDS&size=2" + (cursor == null ? "" : "&before=" + cursor), u.token()), 200);
            seen.addAll(page.ids("items"));
            if (!Boolean.TRUE.equals(page.value("hasNext"))) {
                break;
            }
            cursor = page.text("nextCursor");
        }

        List<Long> newestFirst = new ArrayList<>(created);
        java.util.Collections.reverse(newestFirst);
        assertThat(seen).containsExactlyElementsOf(newestFirst);
    }
}
