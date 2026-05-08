-- 1. Link mechanic jobs to a vehicle in the owner's account
ALTER TABLE mechanic_jobs
  ADD COLUMN IF NOT EXISTS vehicle_id UUID REFERENCES vehicles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_mechanic_jobs_vehicle_id ON mechanic_jobs(vehicle_id);

-- 2. Edit tracking on mechanic job logs
ALTER TABLE mechanic_job_logs
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS edit_notes TEXT;

-- 3. Columns on maintenance_logs so users can see mechanic-sourced records
ALTER TABLE maintenance_logs
  ADD COLUMN IF NOT EXISTS mechanic_job_log_id UUID REFERENCES mechanic_job_logs(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'self',
  ADD COLUMN IF NOT EXISTS edit_notes TEXT;

CREATE INDEX IF NOT EXISTS idx_maintenance_logs_mechanic_job_log_id
  ON maintenance_logs(mechanic_job_log_id)
  WHERE mechanic_job_log_id IS NOT NULL;

-- 4. SECURITY DEFINER function to link a mechanic job to a vehicle by VIN.
--    Called after createJob when a VIN is provided. Bypasses RLS so the mechanic
--    doesn't need direct read access to the owner's vehicles table.
CREATE OR REPLACE FUNCTION link_mechanic_job_by_vin(p_job_id UUID, p_vin TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_vehicle_id UUID;
BEGIN
  SELECT id INTO v_vehicle_id
  FROM vehicles
  WHERE UPPER(TRIM(vin)) = UPPER(TRIM(p_vin))
  LIMIT 1;

  IF v_vehicle_id IS NOT NULL THEN
    UPDATE mechanic_jobs SET vehicle_id = v_vehicle_id WHERE id = p_job_id;
  END IF;

  RETURN v_vehicle_id;
END;
$$;

-- 5. Trigger function: mirror mechanic_job_logs → maintenance_logs.
--    Runs SECURITY DEFINER so it can write to maintenance_logs regardless of RLS.
CREATE OR REPLACE FUNCTION mirror_mechanic_log_to_maintenance()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_vehicle_id UUID;
BEGIN
  SELECT vehicle_id INTO v_vehicle_id
  FROM mechanic_jobs
  WHERE id = NEW.mechanic_job_id;

  IF v_vehicle_id IS NULL THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    INSERT INTO maintenance_logs (
      vehicle_id, category, description, date, mileage, cost, notes,
      created_by_user_id, mechanic_job_log_id, source
    ) VALUES (
      v_vehicle_id, NEW.category, NEW.description, NEW.date,
      NEW.mileage, NEW.cost, NEW.notes,
      NEW.mechanic_user_id, NEW.id, 'mechanic'
    )
    ON CONFLICT (mechanic_job_log_id) DO NOTHING;

  ELSIF TG_OP = 'UPDATE' THEN
    UPDATE maintenance_logs SET
      category    = NEW.category,
      description = NEW.description,
      date        = NEW.date,
      mileage     = NEW.mileage,
      cost        = NEW.cost,
      notes       = NEW.notes,
      edit_notes  = NEW.edit_notes,
      updated_at  = NOW()
    WHERE mechanic_job_log_id = NEW.id;
  END IF;

  RETURN NEW;
END;
$$;

-- Unique constraint so the ON CONFLICT clause above works
ALTER TABLE maintenance_logs
  DROP CONSTRAINT IF EXISTS maintenance_logs_mechanic_job_log_id_unique;
ALTER TABLE maintenance_logs
  ADD CONSTRAINT maintenance_logs_mechanic_job_log_id_unique
  UNIQUE (mechanic_job_log_id);

DROP TRIGGER IF EXISTS mirror_mechanic_log ON mechanic_job_logs;
CREATE TRIGGER mirror_mechanic_log
  AFTER INSERT OR UPDATE ON mechanic_job_logs
  FOR EACH ROW EXECUTE FUNCTION mirror_mechanic_log_to_maintenance();

-- 6. RLS: car owners can read mechanic_jobs that are linked to their vehicles
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE tablename = 'mechanic_jobs'
      AND policyname = 'Users can view mechanic jobs for their vehicles'
  ) THEN
    CREATE POLICY "Users can view mechanic jobs for their vehicles"
    ON mechanic_jobs FOR SELECT
    USING (
      vehicle_id IN (
        SELECT id FROM vehicles WHERE owner_id = auth.uid()
      )
    );
  END IF;
END;
$$;

-- 7. Backfill: mirror any existing logs for jobs that already have vehicle_id set
INSERT INTO maintenance_logs (
  vehicle_id, category, description, date, mileage, cost, notes,
  created_by_user_id, mechanic_job_log_id, source
)
SELECT
  mj.vehicle_id, mpl.category, mpl.description, mpl.date,
  mpl.mileage, mpl.cost, mpl.notes,
  mpl.mechanic_user_id, mpl.id, 'mechanic'
FROM mechanic_job_logs mpl
JOIN mechanic_jobs mj ON mj.id = mpl.mechanic_job_id
WHERE mj.vehicle_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM maintenance_logs ml
    WHERE ml.mechanic_job_log_id = mpl.id
  );
