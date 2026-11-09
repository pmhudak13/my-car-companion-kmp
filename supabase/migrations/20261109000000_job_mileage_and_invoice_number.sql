-- Mechanic feedback (Nov 8 2026): invoices need the odometer reading at drop-off and a
-- per-mechanic invoice number. Numbers start at 1001 per mechanic and are assigned by the DB
-- only, so the client can never pick or change one.

ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS mileage_in integer CHECK (mileage_in IS NULL OR mileage_in >= 0),
  ADD COLUMN IF NOT EXISTS invoice_number integer;

-- Backfill existing jobs in the order they were created.
UPDATE public.mechanic_jobs mj
SET invoice_number = n.num
FROM (
  SELECT id, 1000 + row_number() OVER (PARTITION BY mechanic_user_id ORDER BY created_at, id) AS num
  FROM public.mechanic_jobs
) n
WHERE mj.id = n.id AND mj.invoice_number IS NULL;

ALTER TABLE public.mechanic_jobs ALTER COLUMN invoice_number SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS mechanic_jobs_mechanic_invoice_number_key
  ON public.mechanic_jobs (mechanic_user_id, invoice_number);

-- SECURITY DEFINER so max() sees every job of this mechanic regardless of who inserts it
-- (import RPC, admin). The advisory lock serializes concurrent inserts for one mechanic.
CREATE OR REPLACE FUNCTION public.assign_job_invoice_number()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    NEW.invoice_number := OLD.invoice_number;
    RETURN NEW;
  END IF;

  PERFORM pg_advisory_xact_lock(hashtext('invoice_number:' || NEW.mechanic_user_id::text));
  SELECT COALESCE(MAX(invoice_number), 1000) + 1
    INTO NEW.invoice_number
    FROM public.mechanic_jobs
   WHERE mechanic_user_id = NEW.mechanic_user_id;
  RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.assign_job_invoice_number() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_assign_job_invoice_number ON public.mechanic_jobs;
CREATE TRIGGER trg_assign_job_invoice_number
  BEFORE INSERT OR UPDATE OF invoice_number ON public.mechanic_jobs
  FOR EACH ROW EXECUTE FUNCTION public.assign_job_invoice_number();
