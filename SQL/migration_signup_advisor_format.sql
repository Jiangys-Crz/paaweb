-- Normalize only legacy bracket-wrapped signup advisor lists. Back up first.
BEGIN;
CREATE OR REPLACE FUNCTION pg_temp.signup_advisor_text(raw text)
RETURNS text LANGUAGE plpgsql AS $$
DECLARE body text; parsed jsonb; result text;
BEGIN
    IF raw IS NULL OR left(btrim(raw), 1) <> '[' OR right(btrim(raw), 1) <> ']' THEN
        RETURN raw;
    END IF;
    BEGIN
        parsed := btrim(raw)::jsonb;
        IF jsonb_typeof(parsed) = 'array' THEN
            SELECT coalesce(string_agg(value, '、' ORDER BY position), '') INTO body
            FROM jsonb_array_elements_text(parsed) WITH ORDINALITY AS entry(value, position);
        END IF;
    EXCEPTION WHEN invalid_text_representation THEN
        body := NULL;
    END;
    IF body IS NULL THEN body := substring(btrim(raw) FROM 2 FOR length(btrim(raw)) - 2); END IF;
    SELECT string_agg(name, '、' ORDER BY position) INTO result FROM (
        SELECT position, btrim(CASE
            WHEN (left(btrim(value), 1) = '"' AND right(btrim(value), 1) = '"')
              OR (left(btrim(value), 1) = '''' AND right(btrim(value), 1) = '''')
            THEN substring(btrim(value) FROM 2 FOR greatest(length(btrim(value)) - 2, 0))
            ELSE btrim(value) END) AS name
        FROM regexp_split_to_table(body, E'[、,，;；\\r\\n]+') WITH ORDINALITY AS entry(value, position)
    ) AS names WHERE name <> '';
    RETURN coalesce(result, '');
END;
$$;

UPDATE public.signup_personal SET advisor = pg_temp.signup_advisor_text(advisor)
WHERE left(btrim(advisor), 1) = '[' AND right(btrim(advisor), 1) = ']';
UPDATE public.signup_team SET advisor = pg_temp.signup_advisor_text(advisor)
WHERE left(btrim(advisor), 1) = '[' AND right(btrim(advisor), 1) = ']';
COMMIT;
