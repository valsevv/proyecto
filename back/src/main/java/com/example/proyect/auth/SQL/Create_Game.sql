-- Table: public.game

-- DROP TABLE IF EXISTS public.game;

CREATE TABLE IF NOT EXISTS public.game
(
    game_id bigint NOT NULL DEFAULT nextval('game_game_id_seq'::regclass),
    state jsonb NOT NULL,
    ini_date timestamp with time zone NOT NULL DEFAULT now(),
    end_date timestamp with time zone,
    player1_id bigint NOT NULL,
    player2_id bigint NOT NULL,
    CONSTRAINT game_pkey PRIMARY KEY (game_id),
    CONSTRAINT fk_game_player1 FOREIGN KEY (player1_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE RESTRICT,
    CONSTRAINT fk_game_player2 FOREIGN KEY (player2_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE RESTRICT,
    CONSTRAINT chk_game_distinct_players CHECK (player1_id <> player2_id)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.game
    OWNER to postgres;
-- Index: idx_game_player1_id

-- DROP INDEX IF EXISTS public.idx_game_player1_id;

CREATE INDEX IF NOT EXISTS idx_game_player1_id
    ON public.game USING btree
    (player1_id ASC NULLS LAST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;
-- Index: idx_game_player2_id

-- DROP INDEX IF EXISTS public.idx_game_player2_id;

CREATE INDEX IF NOT EXISTS idx_game_player2_id
    ON public.game USING btree
    (player2_id ASC NULLS LAST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;