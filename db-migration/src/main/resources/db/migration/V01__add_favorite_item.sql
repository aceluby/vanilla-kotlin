CREATE TABLE favorite_thing (
    id              INTEGER GENERATED ALWAYS AS IDENTITY,
    thing VARCHAR(511) NOT NULL,
    created_ts      TIMESTAMP    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    updated_ts      TIMESTAMP    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uidx_thing ON favorite_thing (thing);

ALTER TABLE favorite_thing
    ADD CONSTRAINT unique_thing UNIQUE USING INDEX uidx_thing;
