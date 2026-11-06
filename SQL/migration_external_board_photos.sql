BEGIN;
CREATE TABLE IF NOT EXISTS public.external_board_photo (
    board_id BIGINT NOT NULL REFERENCES public.external_board(board_id) ON DELETE CASCADE,
    participant_id BIGINT NOT NULL CHECK (participant_id >= 0),
    photo_key VARCHAR(36) NOT NULL UNIQUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (board_id, participant_id)
);
COMMIT;
