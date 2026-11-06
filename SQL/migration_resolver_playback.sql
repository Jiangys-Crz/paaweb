-- Playback preferences are per external board; existing boards default to single-step.
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_single_step BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_speed DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (resolver_speed >= 0.25 AND resolver_speed <= 4.0);
