
CREATE SCHEMA IF NOT EXISTS public;
SET search_path TO public;

-- USERS
CREATE TABLE IF NOT EXISTS users (
    user_id               BIGSERIAL PRIMARY KEY,
    username         VARCHAR(50)  NOT NULL,
    hash_password    TEXT         NOT NULL,
    email            VARCHAR(255) NOT NULL,
	wins  			     INT NOT NULL DEFAULT 0,
	losses 			     INT NOT NULL DEFAULT 0,
    score			       INT NOT NULL DEFAULT 0,
    creation_date    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_connection  TIMESTAMPTZ,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email    ON users(email);