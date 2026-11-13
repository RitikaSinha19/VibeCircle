package com.vibecircle.story;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.media.MediaStorage;
import com.vibecircle.media.MediaValidator;
import com.vibecircle.media.ValidatedMedia;
import com.vibecircle.post.Audience;
import com.vibecircle.privacy.AccessPolicyService;
import com.vibecircle.privacy.ProfileAccess;
import com.vibecircle.story.dto.StoryView;
import com.vibecircle.story.dto.TrayItem;
import com.vibecircle.story.dto.ViewerView;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoryService {

    static final int MAX_TEXT = 280;

    private final StoryRepository repo;
    private final AccessPolicyService access;
    private final MediaValidator validator;
    private final MediaStorage storage;
    private final StoryProperties props;

    public StoryService(StoryRepository repo, AccessPolicyService access, MediaValidator validator,
                        MediaStorage storage, StoryProperties props) {
        this.repo = repo;
        this.access = access;
        this.validator = validator;
        this.storage = storage;
        this.props = props;
    }

    @Transactional
    public StoryView create(Long me, String rawText, Audience audience, MultipartFile file) {
        String text = rawText == null ? null : rawText.trim();
        if (text != null && text.isEmpty()) {
            text = null;
        }
        boolean hasFile = file != null && !file.isEmpty();
        if (text == null && !hasFile) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A story needs some text or a photo / video");
        }
        if (text != null && text.length() > MAX_TEXT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Story text is limited to " + MAX_TEXT + " characters");
        }

        String key = null;
        String mediaType = null;
        if (hasFile) {
            ValidatedMedia media = validator.validate(file);          // by content, never by file name
            try (InputStream in = file.getInputStream()) {
                key = storage.store(in, media.extension());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            mediaType = media.kind().name();
            String storedKey = key;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.delete(storedKey);                     // roll the file back with the row
                    }
                }
            });
        }
        Long id = repo.insert(me, key, mediaType, text, audience, props.ttlHours());
        return repo.findOne(me, id).orElseThrow(() -> new IllegalStateException("Story missing after insert"));
    }

    @Transactional(readOnly = true)
    public PageResult<TrayItem> tray(Long me, int page, int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.tray(me, s + 1, p * s), p, s);
    }

    @Transactional(readOnly = true)
    public List<StoryView> mine(Long me) {
        return repo.findMine(me);
    }

    @Transactional(readOnly = true)
    public List<StoryView> byUser(Long me, Long userId) {
        if (access.profileAccess(me, userId) == ProfileAccess.NONE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        return repo.findByAuthor(me, userId);
    }

    @Transactional(readOnly = true)
    public StoryView get(Long me, Long storyId) {
        return repo.findOne(me, storyId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Story not found"));
    }

    @Transactional
    public void recordView(Long me, Long storyId) {
        requireVisible(me, storyId);
        repo.recordView(storyId, me);
    }

    @Transactional(readOnly = true)
    public PageResult<ViewerView> viewers(Long me, Long storyId, int page, int size) {
        if (!repo.isActiveAuthor(storyId, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Story not found");     // only the author sees who watched
        }
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        return PageResult.of(repo.viewers(storyId, s + 1, p * s), p, s);
    }

    @Transactional
    public void delete(Long me, Long storyId) {
        if (!repo.softDelete(storyId, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Story not found");
        }
        // hidden immediately; the file and row are removed by the cleanup job after the retention period
    }

    @Transactional(readOnly = true)
    public StoryMedia openMedia(Long me, Long storyId) {
        requireVisible(me, storyId);
        String key = repo.findMediaKey(storyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This story has no media"));
        return new StoryMedia(storage.load(key), contentType(key));
    }

    public record StoryMedia(Resource resource, String contentType) {
    }

    private void requireVisible(Long me, Long storyId) {
        if (!access.canViewStory(me, storyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Story not found");
        }
    }

    private static String contentType(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1);
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            default -> "image/jpeg";
        };
    }
}
