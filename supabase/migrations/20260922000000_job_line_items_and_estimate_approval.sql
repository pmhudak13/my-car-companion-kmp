-- Line items on mechanic jobs + estimate approval record (Mitchell-style Estimate -> Approved -> Invoiced -> Paid)

-- 1. Line items
CREATE TABLE IF NOT EXISTS public.mechanic_job_line_items (
  id                UUID          DEFAULT gen_random_uuid() PRIMARY KEY,
  mechanic_job_id   UUID          NOT NULL REFERENCES public.mechanic_jobs(id) ON DELETE CASCADE,
  mechanic_user_id  UUID          NOT NULL REFERENCES auth.users(id),
  kind              TEXT          NOT NULL DEFAULT 'labor' CHECK (kind IN ('labor','part','fee')),
  description       TEXT          NOT NULL CHECK (length(trim(description)) > 0),
  quantity          NUMERIC(10,2) NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price        NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
  created_at        TIMESTAMPTZ   DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mechanic_job_line_items_job ON public.mechanic_job_line_items(mechanic_job_id);

ALTER TABLE public.mechanic_job_line_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Mechanic full access to own line items" ON public.mechanic_job_line_items;
CREATE POLICY "Mechanic full access to own line items"
ON public.mechanic_job_line_items FOR ALL TO authenticated
USING (mechanic_user_id = auth.uid())
WITH CHECK (
  mechanic_user_id = auth.uid()
  AND EXISTS (SELECT 1 FROM public.mechanic_jobs mj
              WHERE mj.id = mechanic_job_id AND mj.mechanic_user_id = auth.uid())
);

DROP POLICY IF EXISTS "Owner can read line items for their vehicles" ON public.mechanic_job_line_items;
CREATE POLICY "Owner can read line items for their vehicles"
ON public.mechanic_job_line_items FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.mechanic_jobs mj
    JOIN public.vehicles v ON v.id = mj.vehicle_id
    WHERE mj.id = mechanic_job_line_items.mechanic_job_id
      AND v.owner_id = auth.uid()
  )
);

-- 2. Keep mechanic_jobs.total_cost = sum of line items (runs as the mechanic; their RLS allows the update)
CREATE OR REPLACE FUNCTION public.sync_mechanic_job_total()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
DECLARE
  jid UUID := COALESCE(NEW.mechanic_job_id, OLD.mechanic_job_id);
BEGIN
  UPDATE mechanic_jobs
     SET total_cost = (SELECT SUM(quantity * unit_price) FROM mechanic_job_line_items WHERE mechanic_job_id = jid)
   WHERE id = jid;
  RETURN NULL;
END; $$;

DROP TRIGGER IF EXISTS trg_sync_mechanic_job_total ON public.mechanic_job_line_items;
CREATE TRIGGER trg_sync_mechanic_job_total
AFTER INSERT OR UPDATE OR DELETE ON public.mechanic_job_line_items
FOR EACH ROW EXECUTE FUNCTION public.sync_mechanic_job_total();

-- 3. Estimate approval record
ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS estimate_approved_at    TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS estimate_approved_by    UUID REFERENCES auth.users(id),
  ADD COLUMN IF NOT EXISTS estimate_approved_total NUMERIC;

-- Approval columns can only change through approve_mechanic_job_estimate(), so the record can't be forged
-- by a direct PATCH (e.g. a mechanic setting approved_by to the owner's id).
CREATE OR REPLACE FUNCTION public.guard_estimate_approval()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF (NEW.estimate_approved_at, NEW.estimate_approved_by, NEW.estimate_approved_total)
     IS DISTINCT FROM (OLD.estimate_approved_at, OLD.estimate_approved_by, OLD.estimate_approved_total)
     AND COALESCE(current_setting('app.approving_estimate', true), '') <> '1' THEN
    RAISE EXCEPTION 'Estimate approval can only be recorded via approve_mechanic_job_estimate()';
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_guard_estimate_approval ON public.mechanic_jobs;
CREATE TRIGGER trg_guard_estimate_approval
BEFORE UPDATE ON public.mechanic_jobs
FOR EACH ROW EXECUTE FUNCTION public.guard_estimate_approval();

-- Owner (vehicle linked) or the mechanic (customer approved in person / by phone) records approval.
-- Calling again re-approves at the current total (re-authorization after the job grows).
CREATE OR REPLACE FUNCTION public.approve_mechanic_job_estimate(p_job_id UUID)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM set_config('app.approving_estimate', '1', true);
  UPDATE mechanic_jobs mj
     SET estimate_approved_at = NOW(),
         estimate_approved_by = auth.uid(),
         estimate_approved_total = mj.total_cost
   WHERE mj.id = p_job_id
     AND mj.status = 'open'
     AND mj.total_cost IS NOT NULL
     AND (mj.mechanic_user_id = auth.uid()
          OR EXISTS (SELECT 1 FROM vehicles v WHERE v.id = mj.vehicle_id AND v.owner_id = auth.uid()));
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cannot approve this estimate';
  END IF;
  PERFORM set_config('app.approving_estimate', '', true);
END; $$;

REVOKE ALL ON FUNCTION public.approve_mechanic_job_estimate(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_mechanic_job_estimate(UUID) TO authenticated;
