-- Advisor follow-ups for 20260922000000: Supabase grants anon EXECUTE directly (not via PUBLIC), and pin search_path
REVOKE EXECUTE ON FUNCTION public.approve_mechanic_job_estimate(UUID) FROM anon;
ALTER FUNCTION public.guard_estimate_approval() SET search_path = public;
