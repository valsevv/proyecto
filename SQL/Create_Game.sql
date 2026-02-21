
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

