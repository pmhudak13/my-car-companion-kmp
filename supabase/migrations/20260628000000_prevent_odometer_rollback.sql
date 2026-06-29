-- ============================================================
-- Prevent odometer rollback — June 2026
-- A vehicle's odometer is monotonic: it can only ever go up.
-- This is the one real anti-fraud guard worth enforcing server-side —
-- it blocks odometer rollback (the #1 used-car scam) even against
-- direct API/SQL calls that bypass the app UI.
-- Maintenance logs can still record any historical mileage (backfill);
-- this only guards the vehicle's headline odometer value.
-- Escape hatch for a genuine typo correction:
--   ALTER TABLE public.vehicles DISABLE TRIGGER trg_prevent_odometer_rollback;
--   <fix the row>
--   ALTER TABLE public.vehicles ENABLE TRIGGER trg_prevent_odometer_rollback;
-- ============================================================

CREATE OR REPLACE FUNCTION public.prevent_odometer_rollback()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
  IF NEW.odometer < OLD.odometer THEN
    RAISE EXCEPTION 'Odometer cannot be decreased (current: % %, attempted: %)',
      OLD.odometer, OLD.unit, NEW.odometer;
  END IF;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trg_prevent_odometer_rollback ON public.vehicles;
CREATE TRIGGER trg_prevent_odometer_rollback
  BEFORE UPDATE ON public.vehicles
  FOR EACH ROW
  EXECUTE FUNCTION public.prevent_odometer_rollback();
