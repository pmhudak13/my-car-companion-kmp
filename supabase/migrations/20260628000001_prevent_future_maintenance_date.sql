-- ============================================================
-- Prevent future-dated maintenance — June 2026
-- A service can't have happened in the future. Applies to every
-- maintenance_logs row (self, mechanic, and imported), since nothing
-- legitimate is future-dated. 1-day grace absorbs client/server
-- timezone skew near midnight.
-- This is the only date guard worth enforcing server-side: a hard
-- *backdating* cap is NOT enforced here because Record Import and
-- mechanic records legitimately carry old dates. The backdating cap
-- lives client-side on the self Add Maintenance form only.
-- ============================================================

CREATE OR REPLACE FUNCTION public.prevent_future_maintenance_date()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.date > current_date + 1 THEN
    RAISE EXCEPTION 'Maintenance date cannot be in the future (date: %)', NEW.date;
  END IF;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_prevent_future_maintenance_date ON public.maintenance_logs;
CREATE TRIGGER trg_prevent_future_maintenance_date
  BEFORE INSERT OR UPDATE ON public.maintenance_logs
  FOR EACH ROW
  EXECUTE FUNCTION public.prevent_future_maintenance_date();
