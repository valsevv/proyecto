-- Table: public.ranking

-- DROP TABLE IF EXISTS public.ranking;

CREATE TABLE IF NOT EXISTS public.ranking
(
    ranking_id integer NOT NULL DEFAULT nextval('ranking_ranking_id_seq'::regclass),
    user_id bigint NOT NULL,
    points integer NOT NULL DEFAULT 0,
    reached_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ranking_pkey PRIMARY KEY (ranking_id),
    CONSTRAINT fk_ranking_user_ranking FOREIGN KEY (ranking_id)
        REFERENCES public.ranking (ranking_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT fk_ranking_user_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.ranking
    OWNER to postgres;
-- Index: idx_ranking_points

-- DROP INDEX IF EXISTS public.idx_ranking_points;

CREATE INDEX IF NOT EXISTS idx_ranking_points
    ON public.ranking USING btree
    (points DESC NULLS FIRST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;
-- Index: idx_ranking_reach_date

-- DROP INDEX IF EXISTS public.idx_ranking_reach_date;

CREATE INDEX IF NOT EXISTS idx_ranking_reach_date
    ON public.ranking USING btree
    (reached_at DESC NULLS FIRST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;