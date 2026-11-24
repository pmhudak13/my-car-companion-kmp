-- Bryan's Nov 23 2026 asks: tag job photos (pre-existing damage, pre-scan, post-scan),
-- notes the customer acknowledges, and follow-ups the mechanic sets for both sides.

-- 1. Photo / PDF category, shown as its own section on the invoice.
ALTER TABLE public.mechanic_job_media
  ADD COLUMN IF NOT EXISTS category TEXT NOT NULL DEFAULT 'general'
  CHECK (category IN ('general', 'pre_existing', 'pre_scan', 'post_scan'));

-- 2. Notes added at any stage; the customer (or the mechanic, on the customer's word) acknowledges.
CREATE TABLE IF NOT EXISTS public.mechanic_job_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  mechanic_job_id UUID NOT NULL REFERENCES public.mechanic_jobs(id) ON DELETE CASCADE,
  mechanic_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 4000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  acknowledged_at TIMESTAMPTZ,
  acknowledged_by UUID REFERENCES auth.users(id),
  acknowledge_method TEXT CHECK (acknowledge_method IN ('in_person', 'phone', 'text', 'in_app'))
);
CREATE INDEX IF NOT EXISTS mechanic_job_notes_job_idx ON public.mechanic_job_notes (mechanic_job_id);
ALTER TABLE public.mechanic_job_notes ENABLE ROW LEVEL SECURITY;

-- Mechanic writes notes but can't fake an acknowledgment except through the RPC below.
CREATE POLICY "Mechanic reads own job notes" ON public.mechanic_job_notes
  FOR SELECT USING (mechanic_user_id = auth.uid());
CREATE POLICY "Mechanic adds notes to own jobs" ON public.mechanic_job_notes
  FOR INSERT WITH CHECK (
    mechanic_user_id = auth.uid() AND acknowledged_at IS NULL AND acknowledged_by IS NULL AND acknowledge_method IS NULL
    AND EXISTS (SELECT 1 FROM mechanic_jobs mj WHERE mj.id = mechanic_job_id AND mj.mechanic_user_id = auth.uid()));
-- Only unacknowledged notes can be removed, so the record the customer signed off on stays.
CREATE POLICY "Mechanic deletes unacknowledged notes" ON public.mechanic_job_notes
  FOR DELETE USING (mechanic_user_id = auth.uid() AND acknowledged_at IS NULL);
CREATE POLICY "Owner reads notes for their vehicles" ON public.mechanic_job_notes
  FOR SELECT USING (EXISTS (
    SELECT 1 FROM mechanic_jobs mj JOIN vehicles v ON v.id = mj.vehicle_id
     WHERE mj.id = mechanic_job_notes.mechanic_job_id AND v.owner_id = auth.uid()));

CREATE OR REPLACE FUNCTION public.acknowledge_mechanic_job_note(p_note_id UUID, p_method TEXT DEFAULT 'in_app')
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF p_method NOT IN ('in_person', 'phone', 'text', 'in_app') THEN
    RAISE EXCEPTION 'Unknown acknowledgment method';
  END IF;
  UPDATE mechanic_job_notes n
     SET acknowledged_at = NOW(),
         acknowledged_by = auth.uid(),
         -- the owner acknowledging themselves is always in-app, whatever was passed
         acknowledge_method = CASE WHEN n.mechanic_user_id = auth.uid() THEN p_method ELSE 'in_app' END
    FROM mechanic_jobs mj
   WHERE n.id = p_note_id
     AND mj.id = n.mechanic_job_id
     AND n.acknowledged_at IS NULL
     AND (n.mechanic_user_id = auth.uid()
          OR EXISTS (SELECT 1 FROM vehicles v WHERE v.id = mj.vehicle_id AND v.owner_id = auth.uid()));
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cannot acknowledge this note';
  END IF;
END; $$;
REVOKE ALL ON FUNCTION public.acknowledge_mechanic_job_note(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.acknowledge_mechanic_job_note(UUID, TEXT) TO authenticated;

-- 3. Follow-ups: "check the CV boot in 90 days". The mechanic gets a push on the due date; when the
-- job is linked to the customer's car, the customer also gets a normal reminder on that car.
CREATE TABLE IF NOT EXISTS public.mechanic_follow_ups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  mechanic_job_id UUID NOT NULL REFERENCES public.mechanic_jobs(id) ON DELETE CASCADE,
  mechanic_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 200),
  due_date DATE NOT NULL,
  discount_percent INT CHECK (discount_percent BETWEEN 1 AND 100),
  reminder_id UUID REFERENCES public.reminders(id) ON DELETE SET NULL,
  notified_at TIMESTAMPTZ,
  done_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS mechanic_follow_ups_due_idx ON public.mechanic_follow_ups (due_date) WHERE done_at IS NULL AND notified_at IS NULL;
ALTER TABLE public.mechanic_follow_ups ENABLE ROW LEVEL SECURITY;
-- Writes go through the RPCs below; ending one via complete_mechanic_follow_up() also turns off
-- the customer's reminder, which a plain delete would leave behind.
CREATE POLICY "Mechanic reads own follow-ups" ON public.mechanic_follow_ups
  FOR SELECT USING (mechanic_user_id = auth.uid());
CREATE POLICY "Owner reads follow-ups for their vehicles" ON public.mechanic_follow_ups
  FOR SELECT USING (EXISTS (
    SELECT 1 FROM mechanic_jobs mj JOIN vehicles v ON v.id = mj.vehicle_id
     WHERE mj.id = mechanic_follow_ups.mechanic_job_id AND v.owner_id = auth.uid()));

CREATE OR REPLACE FUNCTION public.create_mechanic_follow_up(
  p_job_id UUID, p_title TEXT, p_due_date DATE, p_discount_percent INT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_job mechanic_jobs%ROWTYPE;
  v_shop TEXT;
  v_reminder UUID;
  v_id UUID;
BEGIN
  SELECT * INTO v_job FROM mechanic_jobs WHERE id = p_job_id AND mechanic_user_id = auth.uid();
  IF NOT FOUND THEN RAISE EXCEPTION 'Job not found'; END IF;
  IF p_due_date < CURRENT_DATE OR p_due_date > CURRENT_DATE + 1095 THEN
    RAISE EXCEPTION 'Pick a date within the next 3 years';
  END IF;

  IF v_job.vehicle_id IS NOT NULL THEN
    SELECT shop_name INTO v_shop FROM mechanic_profiles WHERE user_id = auth.uid();
    INSERT INTO reminders (vehicle_id, type, custom_name, next_due_date, is_active)
    VALUES (v_job.vehicle_id, 'custom',
            left(trim(p_title)
                 || coalesce(' (from ' || v_shop || ')', '')
                 || coalesce(', ' || p_discount_percent || '% off if done by then', ''), 250),
            p_due_date, true)
    RETURNING id INTO v_reminder;
  END IF;

  INSERT INTO mechanic_follow_ups (mechanic_job_id, mechanic_user_id, title, due_date, discount_percent, reminder_id)
  VALUES (p_job_id, auth.uid(), trim(p_title), p_due_date, p_discount_percent, v_reminder)
  RETURNING id INTO v_id;
  RETURN v_id;
END; $$;
REVOKE ALL ON FUNCTION public.create_mechanic_follow_up(UUID, TEXT, DATE, INT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_mechanic_follow_up(UUID, TEXT, DATE, INT) TO authenticated;

-- Mark done (customer came back) and retire the customer's reminder with it.
CREATE OR REPLACE FUNCTION public.complete_mechanic_follow_up(p_id UUID)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_reminder UUID;
BEGIN
  UPDATE mechanic_follow_ups SET done_at = NOW()
   WHERE id = p_id AND mechanic_user_id = auth.uid() AND done_at IS NULL
  RETURNING reminder_id INTO v_reminder;
  IF NOT FOUND THEN RAISE EXCEPTION 'Follow-up not found'; END IF;
  IF v_reminder IS NOT NULL THEN
    UPDATE reminders SET is_active = false WHERE id = v_reminder;
  END IF;
END; $$;
REVOKE ALL ON FUNCTION public.complete_mechanic_follow_up(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.complete_mechanic_follow_up(UUID) TO authenticated;
