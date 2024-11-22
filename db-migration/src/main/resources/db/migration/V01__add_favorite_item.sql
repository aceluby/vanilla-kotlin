CREATE TABLE favorite_item (
    id              INTEGER GENERATED ALWAYS AS IDENTITY,
    item VARCHAR(511) NOT NULL,
    created_ts      TIMESTAMP    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    updated_ts      TIMESTAMP    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uidx_item ON favorite_item (item);

ALTER TABLE favorite_item
    ADD CONSTRAINT unique_item UNIQUE USING INDEX uidx_item;
