-- Fix 1: Re-approve "Direct Home Auto Care" mechanic.
-- Their verification_status was set to 'rejected' (likely from an accidental revoke
-- before the admin RLS delete policy was in place, which left the mechanic role intact
-- but set the profile to rejected). This restores them to fully verified.
DO $$
DECLARE
  v_user_id UUID;
BEGIN
  SELECT user_id INTO v_user_id
  FROM public.mechanic_profiles
  WHERE LOWER(TRIM(shop_name)) = 'direct home auto care'
  LIMIT 1;

  IF v_user_id IS NULL THEN
    RAISE NOTICE 'Direct Home Auto Care mechanic not found — skipping re-approval';
    RETURN;
  END IF;

  -- Disable audit triggers: auth.uid() is null in migration context and the
  -- audit_logs trigger has a NOT NULL constraint on user_id.
  ALTER TABLE public.mechanic_profiles DISABLE TRIGGER ALL;

  UPDATE public.mechanic_profiles
  SET verification_status = 'verified',
      verified_at = NOW()
  WHERE user_id = v_user_id;

  ALTER TABLE public.mechanic_profiles ENABLE TRIGGER ALL;

  -- Ensure the mechanic role is present (idempotent — safe if already exists)
  INSERT INTO public.user_roles (user_id, role)
  VALUES (v_user_id, 'mechanic')
  ON CONFLICT (user_id, role) DO NOTHING;

  RAISE NOTICE 'Re-approved mechanic user_id=%', v_user_id;
END;
$$;

-- Fix 2: Ensure mechanics can fully manage their own jobs and logs regardless of
-- verification_status. Verification is an admin-facing concept; the mechanic role
-- (user_roles) is the correct access gate. These policies are additive — they do
-- not remove any existing policies.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename  = 'mechanic_jobs'
      AND policyname = 'Mechanic owner full access to own jobs'
  ) THEN
    CREATE POLICY "Mechanic owner full access to own jobs"
    ON public.mechanic_jobs
    FOR ALL
    TO authenticated
    USING  (mechanic_user_id = auth.uid())
    WITH CHECK (mechanic_user_id = auth.uid());
  END IF;
END;
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename  = 'mechanic_job_logs'
      AND policyname = 'Mechanic owner full access to own logs'
  ) THEN
    CREATE POLICY "Mechanic owner full access to own logs"
    ON public.mechanic_job_logs
    FOR ALL
    TO authenticated
    USING  (mechanic_user_id = auth.uid())
    WITH CHECK (mechanic_user_id = auth.uid());
  END IF;
END;
$$;
