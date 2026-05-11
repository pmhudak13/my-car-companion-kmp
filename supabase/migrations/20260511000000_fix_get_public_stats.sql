-- Fix get_public_stats RPC to correctly count verified mechanics.
-- Previous version may have filtered by is_available=true or queried the wrong table,
-- causing verified mechanics to not appear in the count.
CREATE OR REPLACE FUNCTION public.get_public_stats()
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN json_build_object(
    'users',     (SELECT COUNT(*) FROM public.profiles),
    'mechanics', (SELECT COUNT(*) FROM public.mechanic_profiles WHERE verification_status = 'verified'),
    'logs',      (SELECT COUNT(*) FROM public.maintenance_logs)
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_public_stats() TO anon;
GRANT EXECUTE ON FUNCTION public.get_public_stats() TO authenticated;
