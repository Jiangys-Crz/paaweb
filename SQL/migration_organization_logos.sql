-- Organization logos and per-board display mode. Run before deploying the backend.
ALTER TABLE public.school ADD COLUMN IF NOT EXISTS logo_file_id BIGINT REFERENCES public.file_system(file_id) ON DELETE SET NULL;
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS organization_display VARCHAR(16) NOT NULL DEFAULT 'school';
