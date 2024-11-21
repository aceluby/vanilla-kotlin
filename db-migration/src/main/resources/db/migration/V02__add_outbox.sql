CREATE TABLE outbox
(
    id          INTEGER GENERATED ALWAYS AS IDENTITY,
    message_key TEXT      NOT NULL,
    headers     BYTEA,
    body        BYTEA,
    created_ts  TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_created ON outbox (created_ts);
