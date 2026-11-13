package com.vibecircle.chat;

import com.vibecircle.chat.dto.ConversationView;
import com.vibecircle.chat.dto.MessagePage;
import com.vibecircle.chat.dto.MessageView;
import com.vibecircle.chat.dto.TypingEvent;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.media.MediaKind;
import com.vibecircle.media.MediaStorage;
import com.vibecircle.media.MediaValidator;
import com.vibecircle.media.ValidatedMedia;
import com.vibecircle.privacy.AccessPolicyService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatService {

    static final int MAX_TEXT = 4000;

    private final ChatRepository repo;
    private final AccessPolicyService access;
    private final PresenceService presence;
    private final ChatPublisher publisher;
    private final MediaValidator validator;
    private final MediaStorage storage;
    private final ApplicationEventPublisher events;

    public ChatService(ChatRepository repo, AccessPolicyService access, PresenceService presence,
                       ChatPublisher publisher, MediaValidator validator, MediaStorage storage,
                       ApplicationEventPublisher events) {
        this.repo = repo;
        this.access = access;
        this.presence = presence;
        this.publisher = publisher;
        this.validator = validator;
        this.storage = storage;
        this.events = events;
    }

    // ---------------------------------------------------------- conversations
    @Transactional
    public ConversationView openConversation(Long me, Long otherUserId) {
        if (me.equals(otherUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot chat with yourself");
        }
        requireCanMessage(me, otherUserId);
        Long id = repo.getOrCreateDirect(me, otherUserId);
        return repo.findConversation(me, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationView> conversations(Long me, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.listConversations(me, s + 1, p * s), p, s);
    }

    @Transactional(readOnly = true)
    public MessagePage history(Long me, Long conversationId, Long before, int size) {
        requireMember(me, conversationId);   // history stays readable after an unfriend; only SENDING is blocked
        int s = PageResult.safeSize(size);
        List<MessageView> rows = repo.history(me, conversationId, before, s + 1);
        boolean hasNext = rows.size() > s;
        List<MessageView> items = hasNext ? rows.subList(0, s) : rows;
        Long next = hasNext ? items.get(items.size() - 1).id() : null;
        return new MessagePage(List.copyOf(items), next, hasNext);
    }

    // ---------------------------------------------------------------- sending
    @Transactional
    public MessageView sendText(Long me, Long conversationId, String text, UUID clientMsgId) {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message cannot be empty");
        }
        if (body.length() > MAX_TEXT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Messages are limited to " + MAX_TEXT + " characters");
        }
        Long other = requireMember(me, conversationId);
        requireCanMessage(me, other);
        return persistAndPublish(me, other, conversationId, "TEXT", body, null, clientMsgId);
    }

    @Transactional
    public MessageView sendImage(Long me, Long conversationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        Long other = requireMember(me, conversationId);
        requireCanMessage(me, other);

        ValidatedMedia media = validator.validate(file);
        if (media.kind() != MediaKind.IMAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only images can be sent in chat");
        }

        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(in, media.extension());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);      // roll back the file together with the database row
                }
            }
        });
        return persistAndPublish(me, other, conversationId, "IMAGE", null, key, null);
    }

    private MessageView persistAndPublish(Long me, Long other, Long conversationId, String type,
                                          String body, String mediaKey, UUID clientMsgId) {
        boolean delivered = presence.isOnline(other);
        Optional<Long> inserted = repo.insertMessage(conversationId, me, type, body, mediaKey, clientMsgId, delivered);
        if (inserted.isEmpty()) {
            // same clientMsgId again = client retry: return the original, do not store or push twice
            return repo.findIdByClientMsgId(conversationId, me, clientMsgId)
                    .flatMap(id -> repo.findMessage(me, id))
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Please try again"));
        }
        Long id = inserted.get();
        repo.touchConversation(conversationId);
        MessageView view = repo.findMessage(me, id)
                .orElseThrow(() -> new IllegalStateException("Message missing after insert"));
        boolean firstUnread = repo.unreadFromSender(conversationId, me, other) == 1;
        events.publishEvent(new MessageSentEvent(view, me, other, firstUnread));
        return view;
    }

    // -------------------------------------------------------- read / typing
    @Transactional
    public void markRead(Long me, Long conversationId, Long upToMessageId) {
        Long other = requireMember(me, conversationId);
        long limit = upToMessageId == null ? Long.MAX_VALUE : upToMessageId;
        repo.markRead(conversationId, me, limit)
                .ifPresent(upTo -> events.publishEvent(new ReadEvent(conversationId, me, other, upTo)));
    }

    /** Not stored. Forwarded to the other person only while messaging is still allowed. */
    public void typing(Long me, Long conversationId, boolean typing) {
        Long other = requireMember(me, conversationId);
        if (access.canMessage(me, other)) {
            publisher.toUser(other, "/queue/typing", new TypingEvent(conversationId, me, typing));
        }
    }

    // ------------------------------------------------ unsend / hide / media
    /** "Delete for everyone": only the sender; the text and the image file are erased. */
    @Transactional
    public void unsend(Long me, Long messageId) {
        ChatRepository.UnsendTarget target = repo.lockOwnMessage(messageId, me)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        repo.unsend(messageId);
        Long other = repo.otherMember(target.conversationId(), me).orElse(me);
        events.publishEvent(new UnsentEvent(target.conversationId(), messageId, me, other));
        if (target.mediaKey() != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(target.mediaKey());
                }
            });
        }
    }

    /** "Delete for me": hides the message from my view only. */
    @Transactional
    public void hideForMe(Long me, Long messageId) {
        Long conversationId = repo.conversationOfMessage(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found"));
        requireMember(me, conversationId);
        repo.hideMessage(messageId, me);
    }

    @Transactional(readOnly = true)
    public ChatMedia openMedia(Long me, Long messageId) {
        ChatRepository.MediaRef ref = repo.findMedia(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Media not found"));
        Long other = repo.otherMember(ref.conversationId(), me)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Media not found"));
        if (access.blockedEitherWay(me, other)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Media not found");
        }
        return new ChatMedia(storage.load(ref.mediaKey()), contentType(ref.mediaKey()));
    }

    public record ChatMedia(Resource resource, String contentType) {
    }

    // ----------------------------------------------------------------- helpers
    private Long requireMember(Long me, Long conversationId) {
        return repo.otherMember(conversationId, me)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private void requireCanMessage(Long me, Long other) {
        if (!access.canMessage(me, other)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only message your friends");
        }
    }

    private static String contentType(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1);
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
