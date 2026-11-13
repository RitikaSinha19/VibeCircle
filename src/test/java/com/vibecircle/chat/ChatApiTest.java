package com.vibecircle.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Multipart;
import com.vibecircle.support.Api.Resp;
import com.vibecircle.support.ApiTestBase;
import com.vibecircle.support.TestUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatApiTest extends ApiTestBase {

    private TestUser a, b, outsider;
    private long conv;

    @BeforeEach
    void setUp() {
        a = newUser("a");
        b = newUser("b");
        outsider = newUser("out");
        befriend(a, b);
        conv = open(a, b);
    }

    private long open(TestUser from, TestUser to) {
        return expect(api.post("/api/chat/conversations", from.token(), json("userId", to.id())), 200).longValue("id");
    }

    private Resp send(TestUser from, String text) {
        return api.post("/api/chat/conversations/" + conv + "/messages", from.token(), json("text", text));
    }

    private long sendOk(TestUser from, String text) {
        return expect(send(from, text), 201).longValue("id");
    }

    private Resp history(TestUser who) {
        return expect(api.get("/api/chat/conversations/" + conv + "/messages", who.token()), 200);
    }

    @Test
    void onlyFriendsCanStartAConversation() {
        expect(api.post("/api/chat/conversations", a.token(), json("userId", outsider.id())), 403);
        expect(api.post("/api/chat/conversations", a.token(), json("userId", a.id())), 400);
    }

    @Test
    void openingAConversationIsIdempotentInBothDirections() {
        assertThat(open(a, b)).isEqualTo(conv);
        assertThat(open(b, a)).isEqualTo(conv);
        long rows = db.sql("SELECT count(*) FROM conversations WHERE direct_low_id = :l AND direct_high_id = :h")
                .param("l", Math.min(a.id(), b.id())).param("h", Math.max(a.id(), b.id())).query(Long.class).single();
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void messagesAreStoredAndReadReceiptsWork() {
        sendOk(a, "first");
        sendOk(a, "second");

        Resp bView = history(b);
        assertThat(bView.strings("items", "text")).containsExactly("second", "first");   // newest first
        assertThat(bView.value("items", 0, "status")).isNull();                          // status is for the sender only

        Resp aBefore = history(a);
        assertThat(aBefore.text("items", 0, "status")).isEqualTo("SENT");               // b is not connected

        expect(api.postEmpty("/api/chat/conversations/" + conv + "/read", b.token()), 204);

        Resp aAfter = history(a);
        assertThat(aAfter.text("items", 0, "status")).isEqualTo("READ");
        assertThat(aAfter.text("items", 1, "status")).isEqualTo("READ");
    }

    @Test
    void unreadCountsAndPreviewsAppearInTheConversationList() {
        sendOk(a, "hello b");
        sendOk(a, "are you there?");

        Resp list = expect(api.get("/api/chat/conversations", b.token()), 200);
        assertThat(list.longValue("items", 0, "unreadCount")).isEqualTo(2);
        assertThat(list.text("items", 0, "lastMessagePreview")).isEqualTo("are you there?");

        expect(api.postEmpty("/api/chat/conversations/" + conv + "/read", b.token()), 204);
        assertThat(expect(api.get("/api/chat/conversations", b.token()), 200).longValue("items", 0, "unreadCount")).isEqualTo(0);
    }

    @Test
    void outsidersCannotReadOrSendIntoSomeoneElsesConversation() {
        sendOk(a, "private");
        expect(api.get("/api/chat/conversations/" + conv + "/messages", outsider.token()), 404);
        expect(send(outsider, "can I join?"), 404);
        expect(api.postEmpty("/api/chat/conversations/" + conv + "/read", outsider.token()), 404);
    }

    @Test
    void retryingWithTheSameClientMessageIdStoresOnce() {
        String body = json("text", "once only", "clientMsgId", UUID.randomUUID().toString());
        long first = expect(api.post("/api/chat/conversations/" + conv + "/messages", a.token(), body), 201).longValue("id");
        long second = expect(api.post("/api/chat/conversations/" + conv + "/messages", a.token(), body), 201).longValue("id");

        assertThat(second).isEqualTo(first);
        assertThat(history(a).ids("items")).containsExactly(first);
    }

    @Test
    void emptyAndOversizedMessagesAreRejected() {
        expect(send(a, "   "), 400);
        expect(send(a, "x".repeat(4001)), 400);
    }

    @Test
    void onlyTheSenderCanUnsendAndItLeavesATombstone() {
        long id = sendOk(a, "oops");

        expect(api.delete("/api/chat/messages/" + id, b.token()), 404);
        expect(api.delete("/api/chat/messages/" + id, a.token()), 204);
        expect(api.delete("/api/chat/messages/" + id, a.token()), 404);      // already unsent

        Resp view = history(b);
        assertThat(view.value("items", 0, "unsent")).isEqualTo(true);
        assertThat(view.value("items", 0, "text")).isNull();
    }

    @Test
    void deleteForMeOnlyHidesTheMessageForThatUser() {
        long id = sendOk(a, "hide me");
        expect(api.postEmpty("/api/chat/messages/" + id + "/hide", b.token()), 204);

        assertThat(history(b).ids("items")).doesNotContain(id);
        assertThat(history(a).ids("items")).contains(id);
        expect(api.postEmpty("/api/chat/messages/" + id + "/hide", outsider.token()), 404);
    }

    @Test
    void blockingStopsMessagingAndHidesTheConversation() {
        sendOk(a, "before the block");
        expect(api.postEmpty("/api/blocks/" + b.id(), a.token()), 204);

        expect(send(a, "after the block"), 403);
        expect(send(b, "after the block"), 403);
        assertThat(expect(api.get("/api/chat/conversations", a.token()), 200).ids("items")).doesNotContain(conv);
        assertThat(expect(api.get("/api/chat/conversations", b.token()), 200).ids("items")).doesNotContain(conv);
    }

    @Test
    void unfriendingStopsSendingButKeepsHistoryReadable() {
        sendOk(a, "still here");
        expect(api.delete("/api/friends/" + b.id(), a.token()), 204);

        expect(send(a, "no longer allowed"), 403);
        assertThat(history(b).strings("items", "text")).contains("still here");
    }

    @Test
    void imagesAreVisibleOnlyToParticipantsAndValidatedByContent() {
        Resp sent = expect(api.multipart("/api/chat/conversations/" + conv + "/images", a.token(),
                new Multipart().file("file", "pic.png", "image/png", PNG)), 201);
        assertThat(sent.text("type")).isEqualTo("IMAGE");
        long messageId = sent.longValue("id");

        expect(api.get("/api/chat/media/" + messageId, b.token()), 200);
        expect(api.get("/api/chat/media/" + messageId, a.token()), 200);
        expect(api.get("/api/chat/media/" + messageId, outsider.token()), 404);

        expect(api.multipart("/api/chat/conversations/" + conv + "/images", a.token(),
                new Multipart().file("file", "fake.png", "image/png", "not an image".getBytes())), 400);

        expect(api.delete("/api/chat/messages/" + messageId, a.token()), 204);
        expect(api.get("/api/chat/media/" + messageId, b.token()), 404);     // unsent: the file is gone
    }

    @Test
    void aStreakOfUnreadMessagesCreatesOnlyOneNotification() {
        sendOk(a, "one");
        sendOk(a, "two");
        sendOk(a, "three");

        List<String> types = expect(api.get("/api/notifications", b.token()), 200).strings("items", "type");
        assertThat(types.stream().filter("NEW_MESSAGE"::equals).count()).isEqualTo(1);
    }
}
