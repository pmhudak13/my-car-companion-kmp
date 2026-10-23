-- Estimate signature (typed name) + mechanic job timer.
-- Typed-name signature on estimate approval: the owner types their full name to authorize the work.
-- Optional at the DB level so older app builds can still approve; the app requires it from owners.

ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS estimate_signed_name TEXT CHECK (length(estimate_signed_name) <= 100);

CREATE OR REPLACE FUNCTION public.guard_estimate_approval()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF (NEW.estimate_approved_at, NEW.estimate_approved_by, NEW.estimate_approved_total, NEW.estimate_approval_method, NEW.estimate_signed_name)
     IS DISTINCT FROM (OLD.estimate_approved_at, OLD.estimate_approved_by, OLD.estimate_approved_total, OLD.estimate_approval_method, OLD.estimate_signed_name)
     AND COALESCE(current_setting('app.approving_estimate', true), '') <> '1' THEN
    RAISE EXCEPTION 'Estimate approval can only be recorded via approve_mechanic_job_estimate()';
  END IF;
  RETURN NEW;
END; $$;

DROP FUNCTION IF EXISTS public.approve_mechanic_job_estimate(UUID, TEXT);
CREATE OR REPLACE FUNCTION public.approve_mechanic_job_estimate(p_job_id UUID, p_method TEXT DEFAULT 'in_app', p_signed_name TEXT DEFAULT NULL)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM set_config('app.approving_estimate', '1', true);
  UPDATE mechanic_jobs mj
     SET estimate_approved_at = NOW(),
         estimate_approved_by = auth.uid(),
         estimate_approved_total = mj.total_cost,
         estimate_approval_method = CASE WHEN mj.mechanic_user_id = auth.uid() THEN p_method ELSE 'in_app' END,
         estimate_signed_name = NULLIF(left(trim(p_signed_name), 100), '')
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

REVOKE ALL ON FUNCTION public.approve_mechanic_job_estimate(UUID, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.approve_mechanic_job_estimate(UUID, TEXT, TEXT) TO authenticated;

-- Job timer: banked seconds plus the start of the running stretch, so it survives leaving the screen.
ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS timer_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS timer_logged_seconds BIGINT NOT NULL DEFAULT 0 CHECK (timer_logged_seconds >= 0);
