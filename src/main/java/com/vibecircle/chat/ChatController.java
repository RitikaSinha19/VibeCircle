package com.vibecircle.chat;

import com.vibecircle.chat.dto.ConversationView;
import com.vibecircle.chat.dto.MarkReadRequest;
import com.vibecircle.chat.dto.MessagePage;
import com.vibecircle.chat.dto.MessageView;
import com.vibecircle.chat.dto.OpenConversationRequest;
import com.vibecircle.chat.dto.PresenceView;
import com.vibecircle.chat.dto.SendTextRequest;
import com.vibecircle.common.ApiException;
import com.vibecircle.common.CurrentUser;
import com.vibecircle.common.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;
    private final PresenceService presence;

    public ChatController(ChatService chat, PresenceService presence) {
        this.chat = chat;
        this.presence = presence;
    }

    /** Get-or-create the one-to-one conversation with a friend. */
    @PostMapping("/conversations")
    public ConversationView open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenConversationRequest req) {
        return chat.openConversation(CurrentUser.id(jwt), req.userId());
    }

    @GetMapping("/conversations")
    public PageResult<ConversationView> conversations(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return chat.conversations(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/conversations/{id}/messages")
    public MessagePage history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                               @RequestParam(required = false) Long before,
                               @RequestParam(defaultValue = "30") int size) {
        return chat.history(CurrentUser.id(jwt), id, before, size);
    }

    /** REST twin of the STOMP /app/chat/send (useful for tests and as a fallback when the socket is down). */
    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                            @Valid @RequestBody SendTextRequest req) {
        return chat.sendText(CurrentUser.id(jwt), id, req.text(), req.clientMsgId());
    }

    @PostMapping(value = "/conversations/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView sendImage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @RequestParam("file") MultipartFile file) {
        return chat.sendImage(CurrentUser.id(jwt), id, file);
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                         @RequestBody(required = false) MarkReadRequest req) {
        chat.markRead(CurrentUser.id(jwt), id, req == null ? null : req.upToMessageId());
    }

    /** Unsend: delete for everyone (sender only). */
    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        chat.unsend(CurrentUser.id(jwt), id);
    }

    /** Delete for me only. */
    @PostMapping("/messages/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        chat.hideForMe(CurrentUser.id(jwt), id);
    }

    @GetMapping("/media/{messageId}")
    public ResponseEntity<Resource> media(@AuthenticationPrincipal Jwt jwt, @PathVariable Long messageId) {
        ChatService.ChatMedia media = chat.openMedia(CurrentUser.id(jwt), messageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(media.resource());
    }

    /** GET /api/chat/presence?userIds=3,10 - friends only; other ids are left out. */
    @GetMapping("/presence")
    public List<PresenceView> presence(@AuthenticationPrincipal Jwt jwt, @RequestParam List<Long> userIds) {
        if (userIds.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At most 100 user ids");
        }
        return presence.presenceOf(CurrentUser.id(jwt), userIds);
    }
}
