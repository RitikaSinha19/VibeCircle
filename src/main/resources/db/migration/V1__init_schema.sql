-- =====================================================================
-- VibeCircle  |  V1__init_schema.sql  |  PostgreSQL 14+
-- Conventions:
--   * BIGINT identity PKs; TIMESTAMPTZ everywhere (store UTC)
--   * Enums as VARCHAR + CHECK (Hibernate @Enumerated(STRING) friendly)
--   * Pair tables (friendships, direct chats) use low_id < high_id
--   * Soft delete via deleted_at where moderation/audit matters
-- =====================================================================

-- ---------- Reference data ------------------------------------------
CREATE TABLE cities (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    country     CHAR(2)      NOT NULL DEFAULT 'IN',          -- ISO 3166-1 alpha-2
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_city UNIQUE (name, state, country)
);

CREATE TABLE interests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category    VARCHAR(50)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    CONSTRAINT uq_interest_name UNIQUE (name)
);
CREATE INDEX idx_interests_category ON interests (category);

-- ---------- Users & auth --------------------------------------------
CREATE TABLE users (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username           VARCHAR(30)  NOT NULL,
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    full_name          VARCHAR(100) NOT NULL,
    date_of_birth      DATE         NOT NULL,                -- needed for age policy
    role               VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status             VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at  TIMESTAMPTZ,
    last_seen_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ,
    CONSTRAINT ck_users_role   CHECK (role   IN ('USER','MODERATOR','ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT ck_users_username CHECK (username ~ '^[A-Za-z0-9_.]{3,30}$')
);
-- case-insensitive uniqueness without needing the citext extension
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX uq_users_email_lower    ON users (lower(email));

-- Refresh tokens (store only a hash; rotate on use; revoke on logout/delete)
CREATE TABLE refresh_tokens (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   CHAR(64)     NOT NULL,                      -- SHA-256 hex
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  BIGINT REFERENCES refresh_tokens(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- Email verification / password reset (single-use, hashed, expiring)
CREATE TABLE verification_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_vtoken_type CHECK (type IN ('EMAIL_VERIFY','PASSWORD_RESET')),
    CONSTRAINT uq_vtoken_hash UNIQUE (token_hash)
);
CREATE INDEX idx_vtokens_user ON verification_tokens (user_id, type);

-- ---------- Profiles & interests ------------------------------------
CREATE TABLE user_profiles (
    user_id               BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    city_id               BIGINT REFERENCES cities(id),
    bio                   VARCHAR(300),
    profile_picture_key   VARCHAR(255),                      -- object-storage key, never a public URL
    cover_picture_key     VARCHAR(255),
    profile_visibility    VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    discoverable          BOOLEAN     NOT NULL DEFAULT TRUE,
    friend_request_policy VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_visibility CHECK (profile_visibility IN ('PUBLIC','PRIVATE')),
    CONSTRAINT ck_friend_req_policy  CHECK (friend_request_policy IN ('EVERYONE','NOBODY'))
);
CREATE INDEX idx_profiles_city_discoverable
    ON user_profiles (city_id) WHERE discoverable AND profile_visibility = 'PUBLIC';

CREATE TABLE user_interests (
    user_id      BIGINT NOT NULL REFERENCES users(id)     ON DELETE CASCADE,
    interest_id  BIGINT NOT NULL REFERENCES interests(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, interest_id)
);
-- reverse index: "who else has this interest?" (core of discovery join)
CREATE INDEX idx_user_interests_interest ON user_interests (interest_id, user_id);

-- ---------- Social graph --------------------------------------------
CREATE TABLE friend_requests (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    CONSTRAINT ck_fr_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    CONSTRAINT ck_fr_not_self CHECK (sender_id <> receiver_id)
);
-- only one PENDING request per direction; history rows are allowed
CREATE UNIQUE INDEX uq_fr_pending_direction
    ON friend_requests (sender_id, receiver_id) WHERE status = 'PENDING';
-- also block A->B and B->A both pending (service should auto-accept, this is the safety net)
CREATE UNIQUE INDEX uq_fr_pending_pair
    ON friend_requests (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id))
    WHERE status = 'PENDING';
CREATE INDEX idx_fr_receiver_pending ON friend_requests (receiver_id, created_at DESC) WHERE status = 'PENDING';
CREATE INDEX idx_fr_sender_pending   ON friend_requests (sender_id,   created_at DESC) WHERE status = 'PENDING';

CREATE TABLE friendships (
    user_low_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_low_id, user_high_id),
    CONSTRAINT ck_friend_order CHECK (user_low_id < user_high_id)
);
CREATE INDEX idx_friendships_high ON friendships (user_high_id, user_low_id);

CREATE TABLE blocks (
    blocker_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_block_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_blocks_blocked ON blocks (blocked_id, blocker_id);

-- "Hide this recommendation" + avoid repeated suggestions
CREATE TABLE hidden_recommendations (
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hidden_user_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, hidden_user_id)
);

-- ---------- Posts ----------------------------------------------------
CREATE TABLE posts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    caption     VARCHAR(2200),
    audience    VARCHAR(15) NOT NULL DEFAULT 'PUBLIC',
    city_id     BIGINT REFERENCES cities(id),                 -- optional city-level tag only
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_post_audience CHECK (audience IN ('PUBLIC','FRIENDS_ONLY'))
);
CREATE INDEX idx_posts_author_time ON posts (author_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_public_time ON posts (created_at DESC)           WHERE deleted_at IS NULL AND audience = 'PUBLIC';

CREATE TABLE post_media (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id       BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    storage_key   VARCHAR(255) NOT NULL,                      -- private bucket key; serve via signed URL
    media_type    VARCHAR(10) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT      NOT NULL CHECK (size_bytes > 0),
    position      SMALLINT    NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pm_type CHECK (media_type IN ('IMAGE','VIDEO')),
    CONSTRAINT uq_pm_position UNIQUE (post_id, position)
);

CREATE TABLE hashtags (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag   VARCHAR(60) NOT NULL CHECK (tag = lower(tag)),
    CONSTRAINT uq_hashtag UNIQUE (tag)
);
CREATE TABLE post_hashtags (
    post_id     BIGINT NOT NULL REFERENCES posts(id)    ON DELETE CASCADE,
    hashtag_id  BIGINT NOT NULL REFERENCES hashtags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, hashtag_id)
);
CREATE INDEX idx_post_hashtags_tag ON post_hashtags (hashtag_id, post_id);

CREATE TABLE post_likes (
    post_id     BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)                            -- makes like idempotent
);
CREATE INDEX idx_post_likes_user ON post_likes (user_id);

CREATE TABLE comments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_comments_post_time ON comments (post_id, created_at) WHERE deleted_at IS NULL;

-- ---------- Stories --------------------------------------------------
CREATE TABLE stories (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_key  VARCHAR(255),
    media_type   VARCHAR(10),
    text_overlay VARCHAR(280),
    audience     VARCHAR(15) NOT NULL DEFAULT 'FRIENDS_ONLY',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ck_story_audience CHECK (audience IN ('PUBLIC','FRIENDS_ONLY')),
    CONSTRAINT ck_story_media    CHECK (media_type IS NULL OR media_type IN ('IMAGE','VIDEO')),
    CONSTRAINT ck_story_content  CHECK (storage_key IS NOT NULL OR text_overlay IS NOT NULL),
    CONSTRAINT ck_story_expiry   CHECK (expires_at > created_at)
);
CREATE INDEX idx_stories_author_active ON stories (author_id, expires_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_stories_expiry        ON stories (expires_at);   -- cleanup job

CREATE TABLE story_views (
    story_id   BIGINT      NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    viewer_id  BIGINT      NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    viewed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (story_id, viewer_id)
);

-- ---------- Chat -----------------------------------------------------
CREATE TABLE conversations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type            VARCHAR(10) NOT NULL DEFAULT 'DIRECT',
    direct_low_id   BIGINT REFERENCES users(id) ON DELETE CASCADE,   -- canonical pair for DIRECT
    direct_high_id  BIGINT REFERENCES users(id) ON DELETE CASCADE,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_conv_type   CHECK (type IN ('DIRECT','GROUP')),
    CONSTRAINT ck_conv_direct CHECK (
        (type = 'DIRECT' AND direct_low_id IS NOT NULL AND direct_high_id IS NOT NULL AND direct_low_id < direct_high_id)
     OR (type = 'GROUP'  AND direct_low_id IS NULL     AND direct_high_id IS NULL))
);
CREATE UNIQUE INDEX uq_conv_direct_pair ON conversations (direct_low_id, direct_high_id) WHERE type = 'DIRECT';

CREATE TABLE conversation_members (
    conversation_id       BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id               BIGINT NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    last_read_message_id  BIGINT,                                       -- read-receipt cursor
    muted                 BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_conv_members_user ON conversation_members (user_id);

CREATE TABLE messages (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id        BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    type             VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    body             VARCHAR(4000),
    media_key        VARCHAR(255),
    client_msg_id    UUID,                                              -- idempotency on retry
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at     TIMESTAMPTZ,
    unsent_at        TIMESTAMPTZ,                                       -- "unsend for everyone"
    CONSTRAINT ck_msg_type    CHECK (type IN ('TEXT','IMAGE')),
    CONSTRAINT ck_msg_content CHECK (body IS NOT NULL OR media_key IS NOT NULL)
);
CREATE INDEX idx_messages_conv_id ON messages (conversation_id, id DESC);  -- keyset pagination
CREATE UNIQUE INDEX uq_messages_client_id ON messages (conversation_id, sender_id, client_msg_id)
    WHERE client_msg_id IS NOT NULL;
ALTER TABLE conversation_members
    ADD CONSTRAINT fk_cm_last_read FOREIGN KEY (last_read_message_id) REFERENCES messages(id) ON DELETE SET NULL;

-- "Delete for me" (per-user hide)
CREATE TABLE message_hidden (
    message_id  BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    PRIMARY KEY (message_id, user_id)
);

-- ---------- Calls ----------------------------------------------------
CREATE TABLE calls (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caller_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    callee_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    call_type    VARCHAR(10) NOT NULL,
    status       VARCHAR(15) NOT NULL DEFAULT 'RINGING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at  TIMESTAMPTZ,
    ended_at     TIMESTAMPTZ,
    CONSTRAINT ck_call_type   CHECK (call_type IN ('VOICE','VIDEO')),
    CONSTRAINT ck_call_status CHECK (status IN ('RINGING','ACTIVE','ENDED','REJECTED','MISSED','CANCELLED','FAILED')),
    CONSTRAINT ck_call_not_self CHECK (caller_id <> callee_id)
);
CREATE INDEX idx_calls_caller ON calls (caller_id, created_at DESC);
CREATE INDEX idx_calls_callee ON calls (callee_id, created_at DESC);

-- ---------- Notifications -------------------------------------------
CREATE TABLE notifications (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    type          VARCHAR(30) NOT NULL,
    entity_type   VARCHAR(20),
    entity_id     BIGINT,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notif_type CHECK (type IN (
        'FRIEND_REQUEST','FRIEND_ACCEPTED','POST_LIKED','NEW_COMMENT',
        'NEW_MESSAGE','INCOMING_CALL','MISSED_CALL','NEW_STORY'))
);
CREATE INDEX idx_notif_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notif_unread    ON notifications (recipient_id) WHERE read_at IS NULL;

CREATE TABLE notification_preferences (
    user_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type     VARCHAR(30) NOT NULL,
    enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, type)
);

-- ---------- Moderation ----------------------------------------------
CREATE TABLE reports (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type   VARCHAR(10) NOT NULL,
    target_id     BIGINT      NOT NULL,
    reason        VARCHAR(30) NOT NULL,
    details       VARCHAR(1000),
    status        VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    reviewed_by   BIGINT REFERENCES users(id) ON DELETE SET NULL,
    resolution    VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at   TIMESTAMPTZ,
    CONSTRAINT ck_report_target CHECK (target_type IN ('USER','POST','COMMENT','MESSAGE')),
    CONSTRAINT ck_report_reason CHECK (reason IN ('SPAM','HARASSMENT','HATE','NUDITY','VIOLENCE','UNDERAGE','IMPERSONATION','OTHER')),
    CONSTRAINT ck_report_status CHECK (status IN ('OPEN','UNDER_REVIEW','ACTIONED','DISMISSED'))
);
-- one open report per reporter per target (stops report spamming)
CREATE UNIQUE INDEX uq_report_open ON reports (reporter_id, target_type, target_id)
    WHERE status IN ('OPEN','UNDER_REVIEW');
CREATE INDEX idx_reports_queue ON reports (status, created_at) WHERE status IN ('OPEN','UNDER_REVIEW');

-- ---------- Seed data ------------------------------------------------
INSERT INTO interests (category, name) VALUES
 ('Sports','Cricket'),('Sports','Football'),('Sports','Badminton'),('Sports','Basketball'),('Sports','Swimming'),
 ('Arts','Sketching'),('Arts','Painting'),('Arts','Photography'),('Arts','Design'),
 ('Music','Singing'),('Music','Dancing'),('Music','Instruments'),('Music','Music Production'),
 ('Technology','Programming'),('Technology','AI'),('Technology','Robotics'),('Technology','Cybersecurity'),
 ('Lifestyle','Travelling'),('Lifestyle','Fitness'),('Lifestyle','Cooking'),('Lifestyle','Reading'),
 ('Education','Engineering'),('Education','Science'),('Education','Research'),('Education','Mathematics'),
 ('Entertainment','Movies'),('Entertainment','Anime'),('Entertainment','Gaming'),('Entertainment','Television'),
 ('Business','Entrepreneurship'),('Business','Startups'),('Business','Finance');

INSERT INTO cities (name, state, country) VALUES
 ('Bengaluru','Karnataka','IN'),('Kolkata','West Bengal','IN'),('Mumbai','Maharashtra','IN'),
 ('Delhi','Delhi','IN'),('Ranchi','Jharkhand','IN'),('Chennai','Tamil Nadu','IN'),('Hyderabad','Telangana','IN');