-- Run before deploying the backend. Existing boards remain disabled.
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_password_hash TEXT;
