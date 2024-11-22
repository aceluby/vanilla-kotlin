CREATE TABLE user_favorite_item
(
    id          INTEGER         GENERATED ALWAYS AS IDENTITY,
    user_name   VARCHAR(1023)   NOT NULL,
    item        VARCHAR(511)    NOT NULL,
    created_ts  TIMESTAMP       NOT NULL    DEFAULT(CURRENT_TIMESTAMP),
    updated_ts  TIMESTAMP       NOT NULL    DEFAULT(CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uidx_user_item ON user_favorite_item (user_name, item);

ALTER TABLE user_favorite_item
ADD CONSTRAINT unique_user_item
UNIQUE USING INDEX uidx_user_item;
