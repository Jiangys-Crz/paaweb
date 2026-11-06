-- Awards and display preferences for the official resolver, isolated per external board.
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_settings TEXT;
