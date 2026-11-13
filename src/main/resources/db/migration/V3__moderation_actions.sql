-- Audit trail of every enforcement decision. Rows are never updated or deleted by the app.
CREATE TABLE moderation_actions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    moderator_id  BIGINT      REFERENCES users(id)   ON DELETE SET NULL,
    action        VARCHAR(30) NOT NULL,
    target_type   VARCHAR(10) NOT NULL,
    target_id     BIGINT      NOT NULL,
    report_id     BIGINT      REFERENCES reports(id) ON DELETE SET NULL,
    note          VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ma_action CHECK (action IN
        ('DISMISS','REMOVE_CONTENT','SUSPEND_USER','REMOVE_AND_SUSPEND','UNSUSPEND_USER')),
    CONSTRAINT ck_ma_target CHECK (target_type IN ('USER','POST','COMMENT','MESSAGE'))
);
CREATE INDEX idx_ma_target    ON moderation_actions (target_type, target_id, created_at DESC);
CREATE INDEX idx_ma_moderator ON moderation_actions (moderator_id, created_at DESC);
