-- ============================================================
-- Security hardening — June 2026
-- Applied to production via Supabase MCP apply_migration.
-- ============================================================

-- 1. claim_vehicle_transfer: trust auth.uid(), not the client-supplied id.
--    Previously SECURITY DEFINER + anon-executable + trusted p_user_id, which
--    let an unauthenticated caller reassign a vehicle to an arbitrary account,
--    bypassing the vehicle_transfers RLS claim policy.
CREATE OR REPLACE FUNCTION public.claim_vehicle_transfer(p_code text, p_user_id uuid DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_transfer RECORD;
  v_claimer uuid := auth.uid();
BEGIN
  IF v_claimer IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  SELECT * INTO v_transfer
  FROM vehicle_transfers
  WHERE transfer_code = UPPER(TRIM(p_code))
    AND claimed_by_id IS NULL
    AND expires_at > NOW()
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Transfer code not found, already claimed, or expired';
  END IF;

  IF v_transfer.sender_id = v_claimer THEN
    RAISE EXCEPTION 'You cannot claim your own transfer';
  END IF;

  UPDATE vehicles
  SET owner_id = v_claimer
  WHERE id = v_transfer.vehicle_id;

  UPDATE vehicle_transfers
  SET claimed_by_id = v_claimer,
      claimed_at    = NOW()
  WHERE id = v_transfer.id;
END;
$function$;

REVOKE EXECUTE ON FUNCTION public.claim_vehicle_transfer(text, uuid) FROM anon, public;
GRANT EXECUTE ON FUNCTION public.claim_vehicle_transfer(text, uuid) TO authenticated;

-- 2. link_mechanic_job_by_vin: only the mechanic who owns the job may link it.
CREATE OR REPLACE FUNCTION public.link_mechanic_job_by_vin(p_job_id uuid, p_vin text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_vehicle_id UUID;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  -- Caller must own the job being linked
  IF NOT EXISTS (
    SELECT 1 FROM mechanic_jobs
    WHERE id = p_job_id AND mechanic_user_id = auth.uid()
  ) THEN
    RAISE EXCEPTION 'Job not found or access denied';
  END IF;

  SELECT id INTO v_vehicle_id
  FROM vehicles
  WHERE UPPER(TRIM(vin)) = UPPER(TRIM(p_vin))
  LIMIT 1;

  IF v_vehicle_id IS NOT NULL THEN
    UPDATE mechanic_jobs SET vehicle_id = v_vehicle_id WHERE id = p_job_id;
  END IF;

  RETURN v_vehicle_id;
END;
$function$;

REVOKE EXECUTE ON FUNCTION public.link_mechanic_job_by_vin(uuid, text) FROM anon, public;
GRANT EXECUTE ON FUNCTION public.link_mechanic_job_by_vin(uuid, text) TO authenticated;

-- 3. Rate-limit functions are internal (service role / triggers only). No client
--    calls them — revoke so clients can't reset or probe the auth throttle.
REVOKE EXECUTE ON FUNCTION public.check_rate_limit(text, text, integer, integer, integer) FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.reset_rate_limit(text, text) FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.cleanup_old_rate_limits() FROM anon, authenticated, public;

-- 4. Relationship gate for notification edge functions. Returns true only when
--    sender and recipient share a real relationship (assignment, chat, or job),
--    so an authenticated user can't push/email arbitrary recipients.
CREATE OR REPLACE FUNCTION public.can_send_notification(p_sender uuid, p_recipient uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
  SELECT
    p_sender = p_recipient
    OR EXISTS (
      SELECT 1 FROM mechanic_assignments ma
      JOIN vehicles v ON v.id = ma.vehicle_id
      WHERE (ma.mechanic_user_id = p_sender AND v.owner_id = p_recipient)
         OR (ma.mechanic_user_id = p_recipient AND v.owner_id = p_sender)
    )
    OR EXISTS (
      SELECT 1 FROM chat_messages cm
      WHERE (cm.sender_id = p_sender AND cm.recipient_id = p_recipient)
         OR (cm.sender_id = p_recipient AND cm.recipient_id = p_sender)
    )
    OR EXISTS (
      SELECT 1 FROM mechanic_jobs mj
      JOIN profiles pr ON pr.user_id = p_recipient
      WHERE mj.mechanic_user_id = p_sender AND lower(mj.client_email) = lower(pr.email)
    )
    OR EXISTS (
      SELECT 1 FROM mechanic_jobs mj
      JOIN profiles pr ON pr.user_id = p_sender
      WHERE mj.mechanic_user_id = p_recipient AND lower(mj.client_email) = lower(pr.email)
    );
$function$;

REVOKE EXECUTE ON FUNCTION public.can_send_notification(uuid, uuid) FROM anon, authenticated, public;
GRANT EXECUTE ON FUNCTION public.can_send_notification(uuid, uuid) TO service_role;

-- 5. Pin search_path on the remaining flagged functions.
ALTER FUNCTION public.handle_first_user_admin() SET search_path TO 'public';
ALTER FUNCTION public.set_updated_at() SET search_path TO 'public';

-- 6. Revoke direct RPC execution of internal trigger/audit functions from
--    untrusted roles (they only run as triggers; never via the REST API).
REVOKE EXECUTE ON FUNCTION public.handle_first_user_admin() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.set_updated_at() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.update_mechanic_rating() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.mirror_mechanic_log_to_maintenance() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.audit_gifted_subscriptions() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.audit_mechanic_verification() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.audit_role_changes() FROM anon, authenticated, public;
REVOKE EXECUTE ON FUNCTION public.audit_vehicle_transfers() FROM anon, authenticated, public;

-- generate_invoice_number is only used by signed-in mechanics; block anon.
REVOKE EXECUTE ON FUNCTION public.generate_invoice_number(uuid) FROM anon, public;
GRANT EXECUTE ON FUNCTION public.generate_invoice_number(uuid) TO authenticated;

-- 7. Public storage buckets: replace broad "anyone can SELECT" listing policies
--    with owner-scoped listing. Public URL downloads bypass RLS on public
--    buckets, so image loading is unaffected; only cross-user enumeration stops.
DROP POLICY IF EXISTS "Avatar images are publicly accessible" ON storage.objects;
CREATE POLICY "Owners can list their mechanic avatars"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'mechanic-avatars' AND (storage.foldername(name))[1] = (auth.uid())::text);

DROP POLICY IF EXISTS "Vehicle photos are publicly accessible" ON storage.objects;
CREATE POLICY "Owners can list their vehicle photos"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'vehicle-photos' AND (storage.foldername(name))[1] = (auth.uid())::text);

DROP POLICY IF EXISTS "Public read mechanic job media" ON storage.objects;
CREATE POLICY "Mechanics can list their job media"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'mechanic-job-media' AND (storage.foldername(name))[1] = (auth.uid())::text);
