-- Sales tax on estimates, and make total_cost purely computed so a typed total can't be silently overwritten

-- 1. Tax rate: a mechanic default, copied onto each new job, editable per job
ALTER TABLE public.mechanic_profiles
  ADD COLUMN IF NOT EXISTS default_tax_rate NUMERIC(5,3) NOT NULL DEFAULT 0
    CHECK (default_tax_rate >= 0 AND default_tax_rate <= 20);

ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,3) NOT NULL DEFAULT 0
    CHECK (tax_rate >= 0 AND tax_rate <= 20);

-- A discount is a negative fee line; parts and labor stay non-negative
ALTER TABLE public.mechanic_job_line_items DROP CONSTRAINT IF EXISTS mechanic_job_line_items_unit_price_check;
ALTER TABLE public.mechanic_job_line_items
  ADD CONSTRAINT mechanic_job_line_items_unit_price_check CHECK (unit_price >= 0 OR kind = 'fee');

-- 2. One place that computes a job total: lines + tax on parts.
-- ponytail: only parts are taxed (CA repair labor is not), and fees/discounts stay out of the tax base
-- so a discount never changes the tax. Add a per-line taxable flag if a state needs one.
CREATE OR REPLACE FUNCTION public.mechanic_job_total(p_job_id UUID, p_tax_rate NUMERIC)
RETURNS NUMERIC LANGUAGE sql STABLE SET search_path = public AS $$
  SELECT CASE WHEN COUNT(*) = 0 THEN NULL ELSE
    ROUND(SUM(quantity * unit_price), 2)
    + ROUND(COALESCE(SUM(quantity * unit_price) FILTER (WHERE kind = 'part'), 0) * p_tax_rate / 100, 2)
  END
  FROM mechanic_job_line_items WHERE mechanic_job_id = p_job_id;
$$;

CREATE OR REPLACE FUNCTION public.sync_mechanic_job_total()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
DECLARE
  jid UUID := COALESCE(NEW.mechanic_job_id, OLD.mechanic_job_id);
BEGIN
  UPDATE mechanic_jobs mj
     SET total_cost = mechanic_job_total(jid, mj.tax_rate)
   WHERE mj.id = jid;
  RETURN NULL;
END; $$;

-- Changing the tax rate re-totals the job; a typed total is kept only while the job has no lines
CREATE OR REPLACE FUNCTION public.sync_job_total_on_job_change()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
DECLARE
  computed NUMERIC := mechanic_job_total(NEW.id, NEW.tax_rate);
BEGIN
  IF computed IS NOT NULL THEN
    NEW.total_cost := computed;
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_sync_job_total_on_job_change ON public.mechanic_jobs;
CREATE TRIGGER trg_sync_job_total_on_job_change
BEFORE UPDATE ON public.mechanic_jobs
FOR EACH ROW EXECUTE FUNCTION public.sync_job_total_on_job_change();

-- 3. New jobs start at the mechanic's default rate
CREATE OR REPLACE FUNCTION public.default_job_tax_rate()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.tax_rate = 0 THEN
    NEW.tax_rate := COALESCE(
      (SELECT default_tax_rate FROM mechanic_profiles WHERE user_id = NEW.mechanic_user_id), 0);
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_default_job_tax_rate ON public.mechanic_jobs;
CREATE TRIGGER trg_default_job_tax_rate
BEFORE INSERT ON public.mechanic_jobs
FOR EACH ROW EXECUTE FUNCTION public.default_job_tax_rate();
