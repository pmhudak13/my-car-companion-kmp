-- Job request board (owners post work, verified mechanics reach out)
-- + photo attachments on chat messages.

-- 1. job_requests
-- vehicle_label is denormalized on purpose: mechanics must never need SELECT on
-- public.vehicles (VIN / plate) just to read the board.
CREATE TABLE IF NOT EXISTS public.job_requests (
  id            UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
  owner_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  vehicle_id    UUID        REFERENCES public.vehicles(id) ON DELETE SET NULL,
  vehicle_label TEXT        NOT NULL,
  title         TEXT        NOT NULL,
  description   TEXT,
  city          TEXT,
  state         TEXT,
  status        TEXT        NOT NULL DEFAULT 'open'
                  CHECK (status IN ('open', 'closed')),
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS job_requests_open_idx
  ON public.job_requests (status, created_at DESC);

ALTER TABLE public.job_requests ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'job_requests'
      AND policyname = 'Owner full access to own requests'
  ) THEN
    CREATE POLICY "Owner full access to own requests"
    ON public.job_requests FOR ALL TO authenticated
    USING  (owner_id = auth.uid())
    WITH CHECK (owner_id = auth.uid());
  END IF;
END; $$;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'job_requests'
      AND policyname = 'Verified mechanics can read open requests'
  ) THEN
    CREATE POLICY "Verified mechanics can read open requests"
    ON public.job_requests FOR SELECT TO authenticated
    USING (
      status = 'open'
      AND EXISTS (
        SELECT 1 FROM public.mechanic_profiles mp
        WHERE mp.user_id = auth.uid()
          AND mp.verification_status = 'verified'
      )
    );
  END IF;
END; $$;

-- 1b. Self-check: the migration refuses to apply if the board leaks.
-- Plants a probe request owned by someone else, confirms a non-mechanic can't
-- see it, then removes it. Skips silently if there aren't two non-mechanic users.
DO $$
DECLARE
  poster UUID;
  snooper UUID;
  probe UUID;
  leaked INT;
BEGIN
  SELECT p.user_id INTO poster FROM public.profiles p
   WHERE NOT EXISTS (SELECT 1 FROM public.mechanic_profiles m WHERE m.user_id = p.user_id)
   ORDER BY p.created_at LIMIT 1;
  SELECT p.user_id INTO snooper FROM public.profiles p
   WHERE p.user_id <> poster
     AND NOT EXISTS (SELECT 1 FROM public.mechanic_profiles m WHERE m.user_id = p.user_id)
   ORDER BY p.created_at LIMIT 1;
  IF poster IS NULL OR snooper IS NULL THEN RETURN; END IF;

  INSERT INTO public.job_requests (owner_id, vehicle_label, title)
  VALUES (poster, 'RLS probe', 'RLS probe') RETURNING id INTO probe;

  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', snooper::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('role', 'authenticated', true);
  SELECT count(*) INTO leaked FROM public.job_requests WHERE id = probe;
  PERFORM set_config('role', 'none', true);

  DELETE FROM public.job_requests WHERE id = probe;

  IF leaked > 0 THEN
    RAISE EXCEPTION 'job_requests RLS leak: a non-mechanic can read other users'' requests';
  END IF;
END; $$;

-- 2. Chat photo attachments
ALTER TABLE public.chat_messages
  ADD COLUMN IF NOT EXISTS image_path TEXT;

-- Private bucket: chat photos carry plates, VINs and driveways.
INSERT INTO storage.buckets (id, name, public)
VALUES ('chat-photos', 'chat-photos', false)
ON CONFLICT (id) DO NOTHING;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'Chat photo upload to own folder'
  ) THEN
    CREATE POLICY "Chat photo upload to own folder"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (
      bucket_id = 'chat-photos'
      AND (storage.foldername(name))[1] = auth.uid()::text
    );
  END IF;
END; $$;

-- Readable by the uploader (needed before the message row exists) and by
-- whoever the message was sent to. Nobody else, ever.
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'Chat photo read by thread participants'
  ) THEN
    CREATE POLICY "Chat photo read by thread participants"
    ON storage.objects FOR SELECT TO authenticated
    USING (
      bucket_id = 'chat-photos'
      AND (
        (storage.foldername(name))[1] = auth.uid()::text
        OR EXISTS (
          SELECT 1 FROM public.chat_messages m
          WHERE m.image_path = storage.objects.name
            AND m.recipient_id = auth.uid()
        )
      )
    );
  END IF;
END; $$;
