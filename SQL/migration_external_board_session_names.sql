-- Run after migration_multiple_external_boards.sql.
-- That migration copied the competition title into the old board-name field.
-- Convert only these legacy defaults; keep administrator-defined session names.
BEGIN;
UPDATE public.external_board AS board
SET name = '正式赛'
FROM public.event AS event
WHERE board.event_id = event.event_id
  AND trim(board.name) = trim(event.name);
COMMIT;
