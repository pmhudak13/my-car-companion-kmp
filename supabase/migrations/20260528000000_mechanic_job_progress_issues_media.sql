-- 1. Add progress_percent to mechanic_jobs
ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS progress_percent INTEGER NOT NULL DEFAULT 0
    CHECK (progress_percent >= 0 AND progress_percent <= 100);

-- 2. mechanic_job_issues
CREATE TABLE IF NOT EXISTS public.mechanic_job_issues (
  id                UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
  mechanic_job_id   UUID        NOT NULL REFERENCES public.mechanic_jobs(id) ON DELETE CASCADE,
  mechanic_user_id  UUID        NOT NULL REFERENCES auth.users(id),
  title             TEXT        NOT NULL,
  description       TEXT,
  estimated_cost    DECIMAL(10,2),
  status            TEXT        NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending','approved','declined')),
  owner_response    TEXT,
  created_at        TIMESTAMPTZ DEFAULT NOW(),
  responded_at      TIMESTAMPTZ
);

ALTER TABLE public.mechanic_job_issues ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'mechanic_job_issues'
      AND policyname = 'Mechanic full access to own issues'
  ) THEN
    CREATE POLICY "Mechanic full access to own issues"
    ON public.mechanic_job_issues FOR ALL TO authenticated
    USING  (mechanic_user_id = auth.uid())
    WITH CHECK (mechanic_user_id = auth.uid());
  END IF;
END; $$;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'mechanic_job_issues'
      AND policyname = 'Owner can read and respond to issues for their vehicles'
  ) THEN
    CREATE POLICY "Owner can read and respond to issues for their vehicles"
    ON public.mechanic_job_issues FOR ALL TO authenticated
    USING (
      EXISTS (
        SELECT 1 FROM public.mechanic_jobs mj
        JOIN public.vehicles v ON v.id = mj.vehicle_id
        WHERE mj.id = mechanic_job_issues.mechanic_job_id
          AND v.owner_id = auth.uid()
      )
    )
    WITH CHECK (
      EXISTS (
        SELECT 1 FROM public.mechanic_jobs mj
        JOIN public.vehicles v ON v.id = mj.vehicle_id
        WHERE mj.id = mechanic_job_issues.mechanic_job_id
          AND v.owner_id = auth.uid()
      )
    );
  END IF;
END; $$;

-- 3. mechanic_job_media
CREATE TABLE IF NOT EXISTS public.mechanic_job_media (
  id                UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
  mechanic_job_id   UUID        NOT NULL REFERENCES public.mechanic_jobs(id) ON DELETE CASCADE,
  mechanic_user_id  UUID        NOT NULL REFERENCES auth.users(id),
  storage_path      TEXT        NOT NULL,
  media_type        TEXT        NOT NULL DEFAULT 'image',
  file_name         TEXT        NOT NULL,
  caption           TEXT,
  created_at        TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.mechanic_job_media ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'mechanic_job_media'
      AND policyname = 'Mechanic full access to own media'
  ) THEN
    CREATE POLICY "Mechanic full access to own media"
    ON public.mechanic_job_media FOR ALL TO authenticated
    USING  (mechanic_user_id = auth.uid())
    WITH CHECK (mechanic_user_id = auth.uid());
  END IF;
END; $$;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'mechanic_job_media'
      AND policyname = 'Owner can read media for their vehicles'
  ) THEN
    CREATE POLICY "Owner can read media for their vehicles"
    ON public.mechanic_job_media FOR SELECT TO authenticated
    USING (
      EXISTS (
        SELECT 1 FROM public.mechanic_jobs mj
        JOIN public.vehicles v ON v.id = mj.vehicle_id
        WHERE mj.id = mechanic_job_media.mechanic_job_id
          AND v.owner_id = auth.uid()
      )
    );
  END IF;
END; $$;

-- 4. Allow owners to see mechanic_jobs linked to their vehicles
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'mechanic_jobs'
      AND policyname = 'Owner can read mechanic jobs for their vehicles'
  ) THEN
    CREATE POLICY "Owner can read mechanic jobs for their vehicles"
    ON public.mechanic_jobs FOR SELECT TO authenticated
    USING (
      vehicle_id IS NOT NULL AND
      EXISTS (
        SELECT 1 FROM public.vehicles v
        WHERE v.id = mechanic_jobs.vehicle_id
          AND v.owner_id = auth.uid()
      )
    );
  END IF;
END; $$;

-- 5. Storage bucket + policies
INSERT INTO storage.buckets (id, name, public)
VALUES ('mechanic-job-media', 'mechanic-job-media', true)
ON CONFLICT (id) DO NOTHING;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'Mechanic can upload job media'
  ) THEN
    CREATE POLICY "Mechanic can upload job media"
    ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'mechanic-job-media');
  END IF;
END; $$;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'Public read mechanic job media'
  ) THEN
    CREATE POLICY "Public read mechanic job media"
    ON storage.objects FOR SELECT TO public
    USING (bucket_id = 'mechanic-job-media');
  END IF;
END; $$;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'Mechanic can delete own job media'
  ) THEN
    CREATE POLICY "Mechanic can delete own job media"
    ON storage.objects FOR DELETE TO authenticated
    USING (bucket_id = 'mechanic-job-media' AND (storage.foldername(name))[1] = auth.uid()::text);
  END IF;
END; $$;
