-- Database: proyect
-- Primero creamos las tablas que estan aca, y luego segun como la modifica postgre tomamos el sql que el crea 
-- y lo ponemos en los sql individuales
-- DROP DATABASE IF EXISTS proyect;

CREATE DATABASE proyect
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'Spanish_Mexico.1252'
    LC_CTYPE = 'Spanish_Mexico.1252'
    LOCALE_PROVIDER = 'libc'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;
-- SCHEMA: public

-- DROP SCHEMA IF EXISTS public ;

CREATE SCHEMA IF NOT EXISTS public
    AUTHORIZATION pg_database_owner;

COMMENT ON SCHEMA public
    IS 'standard public schema';

GRANT USAGE ON SCHEMA public TO PUBLIC;

GRANT ALL ON SCHEMA public TO pg_database_owner;

-------------
-- USERS
CREATE TABLE IF NOT EXISTS users (
    id               BIGSERIAL PRIMARY KEY,
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


-- GAME
CREATE TABLE IF NOT EXISTS game (
    game_id     BIGSERIAL   PRIMARY KEY,
    state       JSONB       NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    player1_id  BIGINT      NOT NULL,
    player2_id  BIGINT      NOT NULL,

    CONSTRAINT chk_game_distinct_players CHECK (player1_id <> player2_id),
    CONSTRAINT fk_game_player1 FOREIGN KEY (player1_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_game_player2 FOREIGN KEY (player2_id) REFERENCES users(user_id) ON DELETE RESTRICT
);


-- Índices por jugador (búsquedas por user)
CREATE INDEX IF NOT EXISTS idx_game_player1_id ON game(player1_id);
CREATE INDEX IF NOT EXISTS idx_game_player2_id ON game(player2_id);


-- RANKING (puntos de cada user en un ranking)

CREATE TABLE IF NOT EXISTS ranking (
    ranking_id   SERIAL      PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    points       INTEGER     NOT NULL DEFAULT 0,
    reach_date   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ranking_user_ranking
      FOREIGN KEY (ranking_id) REFERENCES ranking(ranking_id)
      ON DELETE CASCADE,

    CONSTRAINT fk_ranking_user_user
      FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE
);

-- Índices útiles para ordenar/buscar
CREATE INDEX IF NOT EXISTS idx_ranking_points     ON ranking(points DESC);
CREATE INDEX IF NOT EXISTS idx_ranking_reach_date ON ranking(reach_date DESC);
