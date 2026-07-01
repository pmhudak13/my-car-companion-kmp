-- Security audit Jul 2026: lock down unused public buckets, pin trigger search_path,
-- revoke anon EXECUTE on SECURITY DEFINER helpers. Applied to production 2026-07-01.

-- Lock down unused/empty public buckets (no code references, zero objects).
-- mechanic-job-media stays public: app hardcodes public URLs for it.
UPDATE storage.buckets SET public = false
WHERE id IN ('Avatars', 'maintenance-attachments', 'Maintenance-photos', 'mechanic-avatars', 'vehicle-photos');

-- Pin search_path on trigger functions (advisor: mutable search_path)
ALTER FUNCTION public.prevent_odometer_rollback() SET search_path = public;
ALTER FUNCTION public.prevent_future_maintenance_date() SET search_path = public;

-- Remove anon/PUBLIC EXECUTE on SECURITY DEFINER helpers (advisor). Grants were via
-- PUBLIC, so revoke there and re-grant to authenticated only.
-- get_public_stats intentionally stays anon-callable for the landing page.
REVOKE EXECUTE ON FUNCTION public.has_role(uuid, public.app_role) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.owns_vehicle(uuid, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.is_assigned_mechanic(uuid, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.claim_vehicle_transfer(text, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.generate_invoice_number(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.link_mechanic_job_by_vin(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.has_role(uuid, public.app_role) TO authenticated;
GRANT EXECUTE ON FUNCTION public.owns_vehicle(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_assigned_mechanic(uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.claim_vehicle_transfer(text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.generate_invoice_number(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.link_mechanic_job_by_vin(uuid, text) TO authenticated;
