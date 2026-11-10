package com.vibecircle.post;

import com.vibecircle.common.ApiException;
import com.vibecircle.common.PageResult;
import com.vibecircle.media.MediaStorage;
import com.vibecircle.media.MediaValidator;
import com.vibecircle.media.ValidatedMedia;
import com.vibecircle.post.dto.FeedPage;
import com.vibecircle.post.dto.LikeResponse;
import com.vibecircle.post.dto.PostView;
import com.vibecircle.post.dto.UpdatePostRequest;
import com.vibecircle.privacy.AccessPolicyService;
import com.vibecircle.privacy.ProfileAccess;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostService {

    static final int MAX_CAPTION = 2200;
    static final int MAX_HASHTAGS = 30;
    // ASCII only, so Java lower-casing always agrees with the DB CHECK (tag = lower(tag))
    private static final Pattern HASHTAG = Pattern.compile("(?<![A-Za-z0-9_])#([A-Za-z0-9_]{1,50})");

    private final PostRepository repo;
    private final AccessPolicyService access;
    private final MediaValidator validator;
    private final MediaStorage storage;
    private final ApplicationEventPublisher events;

    public PostService(PostRepository repo, AccessPolicyService access, MediaValidator validator,
                       MediaStorage storage, ApplicationEventPublisher events) {
        this.repo = repo;
        this.access = access;
        this.validator = validator;
        this.storage = storage;
        this.events = events;
    }

    // ---------------------------------------------------------------- create
    @Transactional
    public PostView create(Long me, String rawCaption, Audience audience, Long cityId, List<MultipartFile> files) {
        String caption = normalize(rawCaption);
        List<MultipartFile> uploads = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();

        if (caption == null && uploads.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A post needs a caption or at least one file");
        }
        if (caption != null && caption.length() > MAX_CAPTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Caption is limited to " + MAX_CAPTION + " characters");
        }
        if (uploads.size() > MediaValidator.MAX_FILES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At most " + MediaValidator.MAX_FILES + " files per post");
        }
        if (cityId != null && !repo.cityExists(cityId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown city");
        }

        // validate EVERY file before storing any of them
        List<ValidatedMedia> validated = uploads.stream().map(validator::validate).toList();

        // if anything fails or rolls back, delete the files we already wrote
        List<String> storedKeys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storedKeys.forEach(storage::delete);
                }
            }
        });

        Long postId = repo.insertPost(me, caption, audience, cityId);
        int position = 0;
        for (ValidatedMedia m : validated) {
            String key;
            try (InputStream in = m.file().getInputStream()) {
                key = storage.store(in, m.extension());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            storedKeys.add(key);
            repo.insertMedia(postId, key, m.kind().name(), m.contentType(), m.size(), position++);
        }
        repo.replaceHashtags(postId, extractHashtags(caption));
        return view(me, postId);
    }

    // ------------------------------------------------------------------ read
    @Transactional(readOnly = true)
    public PostView get(Long me, Long postId) {
        return view(me, postId);
    }

    @Transactional(readOnly = true)
    public FeedPage feed(Long me, FeedScope scope, Long before, int size) {
        int s = PageResult.safeSize(size);
        List<PostView> rows = repo.feed(me, scope == FeedScope.FRIENDS, before, s + 1);
        boolean hasNext = rows.size() > s;
        List<PostView> items = hasNext ? rows.subList(0, s) : rows;
        Long next = hasNext ? items.get(items.size() - 1).id() : null;
        return new FeedPage(List.copyOf(items), next, hasNext);
    }

    @Transactional(readOnly = true)
    public FeedPage postsBy(Long me, Long authorId, Long before, int size) {
        if (access.profileAccess(me, authorId) == ProfileAccess.NONE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        int s = PageResult.safeSize(size);
        List<PostView> rows = repo.byAuthor(me, authorId, before, s + 1);
        boolean hasNext = rows.size() > s;
        List<PostView> items = hasNext ? rows.subList(0, s) : rows;
        Long next = hasNext ? items.get(items.size() - 1).id() : null;
        return new FeedPage(List.copyOf(items), next, hasNext);
    }

    // ---------------------------------------------------------- edit / delete
    @Transactional
    public PostView update(Long me, Long postId, UpdatePostRequest req) {
        boolean setCaption = req.caption() != null;
        if (!setCaption && req.audience() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to update");
        }
        String caption = setCaption ? normalize(req.caption()) : null;
        if (!repo.updatePost(postId, me, setCaption, caption, req.audience())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");   // missing or not yours
        }
        if (setCaption) {
            repo.replaceHashtags(postId, extractHashtags(caption));
        }
        return view(me, postId);
    }

    @Transactional
    public void delete(Long me, Long postId) {
        if (!repo.softDelete(postId, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");
        }
        // Files stay on disk for now; a cleanup job will remove media of deleted posts later.
    }

    // ----------------------------------------------------------------- likes
    @Transactional
    public LikeResponse like(Long me, Long postId) {
        requireVisible(me, postId);
        if (repo.like(postId, me) > 0) {
            repo.findAuthorId(postId).filter(a -> !a.equals(me))
                    .ifPresent(a -> events.publishEvent(new PostLikedEvent(postId, me, a)));
        }
        return new LikeResponse(true, repo.likeCount(postId));
    }

    @Transactional
    public LikeResponse unlike(Long me, Long postId) {
        requireVisible(me, postId);
        repo.unlike(postId, me);
        return new LikeResponse(false, repo.likeCount(postId));
    }

    // --------------------------------------------------------------- helpers
    private PostView view(Long me, Long postId) {
        return repo.findView(me, postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private void requireVisible(Long me, Long postId) {
        if (!access.canViewPost(me, postId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }

    private static String normalize(String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }

    static Set<String> extractHashtags(String caption) {
        Set<String> tags = new LinkedHashSet<>();
        if (caption == null) return tags;
        Matcher m = HASHTAG.matcher(caption);
        while (m.find() && tags.size() < MAX_HASHTAGS) {
            tags.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }
}
