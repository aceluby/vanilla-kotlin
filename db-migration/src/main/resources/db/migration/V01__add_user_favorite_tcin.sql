CREATE TABLE user_favorite_tcin
(
    id          INTEGER         GENERATED ALWAYS AS IDENTITY,
    user_name   VARCHAR(1023)   NOT NULL,
    tcin        VARCHAR(511)    NOT NULL,
    created_ts  TIMESTAMP       NOT NULL    DEFAULT(CURRENT_TIMESTAMP),
    updated_ts  TIMESTAMP       NOT NULL    DEFAULT(CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uidx_user_tcin ON user_favorite_tcin (user_name, tcin);

ALTER TABLE user_favorite_tcin
ADD CONSTRAINT unique_user_tcin
UNIQUE USING INDEX uidx_user_tcin;
