-- Canned jobs (saved line-item templates) + how each approval was given

-- 1. Canned jobs: private to each mechanic
CREATE TABLE IF NOT EXISTS public.mechanic_canned_jobs (
  id                UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
  mechanic_user_id  UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name              TEXT        NOT NULL CHECK (length(trim(name)) > 0),
  lines             JSONB       NOT NULL CHECK (jsonb_typeof(lines) = 'array' AND jsonb_array_length(lines) > 0),
  created_at        TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.mechanic_canned_jobs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Mechanic full access to own canned jobs" ON public.mechanic_canned_jobs;
CREATE POLICY "Mechanic full access to own canned jobs"
ON public.mechanic_canned_jobs FOR ALL TO authenticated
USING (mechanic_user_id = auth.uid())
WITH CHECK (mechanic_user_id = auth.uid());

-- 2. Approval method on the estimate
ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS estimate_approval_method TEXT
    CHECK (estimate_approval_method IN ('in_app','in_person','phone','text'));

CREATE OR REPLACE FUNCTION public.guard_estimate_approval()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF (NEW.estimate_approved_at, NEW.estimate_approved_by, NEW.estimate_approved_total, NEW.estimate_approval_method)
     IS DISTINCT FROM (OLD.estimate_approved_at, OLD.estimate_approved_by, OLD.estimate_approved_total, OLD.estimate_approval_method)
     AND COALESCE(current_setting('app.approving_estimate', true), '') <> '1' THEN
    RAISE EXCEPTION 'Estimate approval can only be recorded via approve_mechanic_job_estimate()';
  END IF;
  RETURN NEW;
END; $$;

DROP FUNCTION IF EXISTS public.approve_mechanic_job_estimate(UUID);
-- Owner approvals are always 'in_app'; the mechanic records how the customer OK'd it.
CREATE OR REPLACE FUNCTION public.approve_mechanic_job_estimate(p_job_id UUID, p_method TEXT DEFAULT 'in_app')
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM set_config('app.approving_estimate', '1', true);
  UPDATE mechanic_jobs mj
     SET estimate_approved_at = NOW(),
         estimate_approved_by = auth.uid(),
         estimate_approved_total = mj.total_cost,
         estimate_approval_method = CASE WHEN mj.mechanic_user_id = auth.uid() THEN p_method ELSE 'in_app' END
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

REVOKE ALL ON FUNCTION public.approve_mechanic_job_estimate(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.approve_mechanic_job_estimate(UUID, TEXT) TO authenticated;

-- 3. Approval method + responder on issues. The server stamps who/when; owners are always 'in_app'.
ALTER TABLE public.mechanic_job_issues
  ADD COLUMN IF NOT EXISTS approval_method TEXT CHECK (approval_method IN ('in_app','in_person','phone','text')),
  ADD COLUMN IF NOT EXISTS responded_by UUID REFERENCES auth.users(id);

CREATE OR REPLACE FUNCTION public.stamp_issue_response()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.status IS DISTINCT FROM OLD.status THEN
    NEW.responded_by := auth.uid();
    NEW.responded_at := NOW();
    IF auth.uid() IS DISTINCT FROM NEW.mechanic_user_id THEN
      NEW.approval_method := 'in_app';
    END IF;
  ELSE
    -- response record is immutable outside a status change
    NEW.responded_by := OLD.responded_by;
    NEW.responded_at := OLD.responded_at;
    NEW.approval_method := OLD.approval_method;
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_stamp_issue_response ON public.mechanic_job_issues;
CREATE TRIGGER trg_stamp_issue_response
BEFORE UPDATE ON public.mechanic_job_issues
FOR EACH ROW EXECUTE FUNCTION public.stamp_issue_response();
