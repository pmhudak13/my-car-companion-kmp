-- A mechanic's job (and its service records) reaches the customer's car once both exist.
-- Before this, a job only linked by VIN at the moment it was created, so customers invited
-- afterwards never saw their history. The gate is the customer's confirmed sign-in email
-- matching the job's client email; a VIN alone is printed on the windshield, so it isn't enough.

-- The customer's car for a job: same confirmed email, then same VIN (when both have one)
-- or same year/make/model.
CREATE OR REPLACE FUNCTION public.find_customer_vehicle(p_email TEXT, p_vin TEXT, p_year INT, p_make TEXT, p_model TEXT)
RETURNS UUID LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT v.id
    FROM vehicles v
    JOIN auth.users u ON u.id = v.owner_id
   WHERE u.email_confirmed_at IS NOT NULL
     AND lower(trim(u.email)) = lower(trim(p_email))
     AND CASE WHEN NULLIF(trim(p_vin), '') IS NOT NULL AND NULLIF(trim(v.vin), '') IS NOT NULL
              THEN upper(trim(v.vin)) = upper(trim(p_vin))
              ELSE v.year = p_year AND lower(trim(v.make)) = lower(trim(p_make)) AND lower(trim(v.model)) = lower(trim(p_model))
         END
   ORDER BY v.created_at
   LIMIT 1
$$;

-- Copies a job's existing service records onto the car it just linked to (new ones mirror via mirror_mechanic_log).
CREATE OR REPLACE FUNCTION public.backfill_job_history(p_job_id UUID, p_vehicle_id UUID)
RETURNS VOID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  INSERT INTO maintenance_logs (vehicle_id, category, description, date, mileage, cost, notes,
                                created_by_user_id, mechanic_job_log_id, source)
  SELECT p_vehicle_id, l.category, l.description, l.date, l.mileage, l.cost, l.notes,
         l.mechanic_user_id, l.id, 'mechanic'
    FROM mechanic_job_logs l
   WHERE l.mechanic_job_id = p_job_id
  ON CONFLICT (mechanic_job_log_id) DO NOTHING;
$$;

REVOKE ALL ON FUNCTION public.find_customer_vehicle(TEXT, TEXT, INT, TEXT, TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.backfill_job_history(UUID, UUID) FROM PUBLIC, anon, authenticated;

-- Job side: a new job, or a mechanic adding/fixing the client email or car details.
CREATE OR REPLACE FUNCTION public.link_job_to_customer_vehicle()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF NEW.vehicle_id IS NULL AND NULLIF(trim(NEW.client_email), '') IS NOT NULL THEN
    NEW.vehicle_id := find_customer_vehicle(NEW.client_email, NEW.vehicle_vin, NEW.vehicle_year, NEW.vehicle_make, NEW.vehicle_model);
    IF NEW.vehicle_id IS NOT NULL AND TG_OP = 'UPDATE' THEN
      PERFORM backfill_job_history(NEW.id, NEW.vehicle_id);
    END IF;
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_link_job_to_customer_vehicle ON public.mechanic_jobs;
CREATE TRIGGER trg_link_job_to_customer_vehicle
  BEFORE INSERT OR UPDATE OF client_email, vehicle_vin, vehicle_year, vehicle_make, vehicle_model ON public.mechanic_jobs
  FOR EACH ROW EXECUTE FUNCTION public.link_job_to_customer_vehicle();

-- Car side: the invited customer adds (or corrects) their car.
CREATE OR REPLACE FUNCTION public.link_vehicle_to_mechanic_jobs()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_email TEXT;
  v_job   UUID;
BEGIN
  SELECT lower(trim(email)) INTO v_email FROM auth.users WHERE id = NEW.owner_id AND email_confirmed_at IS NOT NULL;
  IF v_email IS NULL THEN RETURN NEW; END IF;

  FOR v_job IN
    UPDATE mechanic_jobs j SET vehicle_id = NEW.id
     WHERE j.vehicle_id IS NULL
       AND lower(trim(j.client_email)) = v_email
       AND find_customer_vehicle(j.client_email, j.vehicle_vin, j.vehicle_year, j.vehicle_make, j.vehicle_model) = NEW.id
    RETURNING j.id
  LOOP
    PERFORM backfill_job_history(v_job, NEW.id);
  END LOOP;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_link_vehicle_to_mechanic_jobs ON public.vehicles;
CREATE TRIGGER trg_link_vehicle_to_mechanic_jobs
  AFTER INSERT OR UPDATE OF vin, year, make, model, owner_id ON public.vehicles
  FOR EACH ROW EXECUTE FUNCTION public.link_vehicle_to_mechanic_jobs();

-- Bulk history import now carries the customer's email, VIN, and mileage, and writes one service
-- record per job so the history shows on the customer's car, not just in the mechanic's estimates.
CREATE OR REPLACE FUNCTION public.import_mechanic_job_history(p_jobs JSONB)
RETURNS INT LANGUAGE plpgsql SECURITY INVOKER SET search_path = public AS $$
DECLARE
  uid UUID := auth.uid();
  j   JSONB;
  jid UUID;
  d   TIMESTAMPTZ;
  n   INT := 0;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'Not authenticated'; END IF;
  IF NOT EXISTS (SELECT 1 FROM mechanic_profiles WHERE user_id = uid) THEN
    RAISE EXCEPTION 'Only mechanics can import job history';
  END IF;
  IF jsonb_typeof(p_jobs) IS DISTINCT FROM 'array' OR jsonb_array_length(p_jobs) NOT BETWEEN 1 AND 500 THEN
    RAISE EXCEPTION 'Import between 1 and 500 jobs at a time';
  END IF;

  FOR j IN SELECT value FROM jsonb_array_elements(p_jobs) LOOP
    d := (j->>'date')::date;
    IF d > now() THEN RAISE EXCEPTION 'Job date % is in the future', j->>'date'; END IF;
    IF jsonb_typeof(j->'lines') IS DISTINCT FROM 'array' OR jsonb_array_length(j->'lines') = 0 THEN
      RAISE EXCEPTION 'Every job needs at least one line';
    END IF;

    INSERT INTO mechanic_jobs (mechanic_user_id, client_name, client_email, vehicle_year, vehicle_make, vehicle_model,
                               vehicle_vin, description, status, progress_percent, payment_received,
                               created_at, completed_at, tax_rate, imported)
    VALUES (uid, j->>'client_name', NULLIF(trim(j->>'client_email'), ''), (j->>'year')::int, j->>'make', j->>'model',
            NULLIF(trim(j->>'vin'), ''), NULLIF(trim(j->>'description'), ''), 'completed', 100, true,
            d, d, COALESCE((j->>'tax_rate')::numeric, 0), true)
    RETURNING id INTO jid;

    -- created_at = the job date, so "last charged" price memory orders history correctly
    INSERT INTO mechanic_job_line_items (mechanic_job_id, mechanic_user_id, kind, description, quantity, unit_price, created_at)
    SELECT jid, uid, l->>'kind', l->>'description', (l->>'quantity')::numeric, (l->>'unit_price')::numeric, d
      FROM jsonb_array_elements(j->'lines') AS l;

    -- The customer-facing service record; total_cost was just synced from the line items
    INSERT INTO mechanic_job_logs (mechanic_job_id, mechanic_user_id, category, description, date, mileage, cost)
    SELECT jid, uid, COALESCE(NULLIF(j->>'category', ''), 'Other'),
           COALESCE(NULLIF(trim(j->>'description'), ''),
                    (SELECT string_agg(l->>'description', ', ') FROM jsonb_array_elements(j->'lines') AS l)),
           d::date, COALESCE((j->>'mileage')::int, 0), mj.total_cost
      FROM mechanic_jobs mj WHERE mj.id = jid;

    n := n + 1;
  END LOOP;
  RETURN n;
END; $$;

REVOKE ALL ON FUNCTION public.import_mechanic_job_history(JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.import_mechanic_job_history(JSONB) TO authenticated;

-- Link the jobs that were already stuck. Naming client_email in SET fires the job-side trigger.
UPDATE public.mechanic_jobs SET client_email = client_email
 WHERE vehicle_id IS NULL AND NULLIF(trim(client_email), '') IS NOT NULL;
