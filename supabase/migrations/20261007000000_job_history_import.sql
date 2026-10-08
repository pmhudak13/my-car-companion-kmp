-- Job history import: a new mechanic's past jobs come in as completed jobs with real line items,
-- so "Copy from past job", price memory, and the labor guide work from their first day.

ALTER TABLE public.mechanic_jobs ADD COLUMN IF NOT EXISTS imported BOOLEAN NOT NULL DEFAULT false;

-- An imported job keeps the tax rate from its old invoice (0 included) rather than today's default.
CREATE OR REPLACE FUNCTION public.default_job_tax_rate()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.tax_rate = 0 AND NOT NEW.imported THEN
    NEW.tax_rate := COALESCE(
      (SELECT default_tax_rate FROM mechanic_profiles WHERE user_id = NEW.mechanic_user_id), 0);
  END IF;
  RETURN NEW;
END; $$;

-- One transaction per file: a bad row rolls back the whole import, so history is never half there.
-- SECURITY INVOKER: the mechanic's own RLS applies to every insert.
CREATE OR REPLACE FUNCTION public.import_mechanic_job_history(p_jobs JSONB)
RETURNS INT LANGUAGE plpgsql SECURITY INVOKER SET search_path = public AS $$
DECLARE
  uid UUID := auth.uid();
  j   JSONB;
  jid UUID;
  d   TIMESTAMPTZ;
  n   INT := 0;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'Not authenticated'; END IF;
  IF NOT EXISTS (SELECT 1 FROM mechanic_profiles WHERE user_id = uid) THEN
    RAISE EXCEPTION 'Only mechanics can import job history';
  END IF;
  IF jsonb_typeof(p_jobs) IS DISTINCT FROM 'array' OR jsonb_array_length(p_jobs) NOT BETWEEN 1 AND 500 THEN
    RAISE EXCEPTION 'Import between 1 and 500 jobs at a time';
  END IF;

  FOR j IN SELECT value FROM jsonb_array_elements(p_jobs) LOOP
    d := (j->>'date')::date;
    IF d > now() THEN RAISE EXCEPTION 'Job date % is in the future', j->>'date'; END IF;
    IF jsonb_typeof(j->'lines') IS DISTINCT FROM 'array' OR jsonb_array_length(j->'lines') = 0 THEN
      RAISE EXCEPTION 'Every job needs at least one line';
    END IF;

    INSERT INTO mechanic_jobs (mechanic_user_id, client_name, vehicle_year, vehicle_make, vehicle_model,
                               description, status, progress_percent, payment_received,
                               created_at, completed_at, tax_rate, imported)
    VALUES (uid, j->>'client_name', (j->>'year')::int, j->>'make', j->>'model',
            NULLIF(trim(j->>'description'), ''), 'completed', 100, true,
            d, d, COALESCE((j->>'tax_rate')::numeric, 0), true)
    RETURNING id INTO jid;

    -- created_at = the job date, so "last charged" price memory orders history correctly
    INSERT INTO mechanic_job_line_items (mechanic_job_id, mechanic_user_id, kind, description, quantity, unit_price, created_at)
    SELECT jid, uid, l->>'kind', l->>'description', (l->>'quantity')::numeric, (l->>'unit_price')::numeric, d
      FROM jsonb_array_elements(j->'lines') AS l;

    n := n + 1;
  END LOOP;
  RETURN n;
END; $$;

REVOKE ALL ON FUNCTION public.import_mechanic_job_history(JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.import_mechanic_job_history(JSONB) TO authenticated;
