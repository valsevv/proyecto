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
      FOREIGN KEY (user_id) REFERENCES users(user_id)
      ON DELETE CASCADE
);

-- Índices útiles para ordenar/buscar
CREATE INDEX IF NOT EXISTS idx_ranking_points     ON ranking(points DESC);
CREATE INDEX IF NOT EXISTS idx_ranking_reach_date ON ranking(reach_date DESC);