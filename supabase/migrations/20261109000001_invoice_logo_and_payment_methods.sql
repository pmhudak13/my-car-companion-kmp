-- Invoices (mechanic feedback, Nov 8 2026): a shop logo, the ways a shop takes payment, invoice
-- terms, and an unguessable per-job token so the customer can open the invoice without an account.

ALTER TABLE public.mechanic_profiles
  ADD COLUMN IF NOT EXISTS logo_path      text,
  ADD COLUMN IF NOT EXISTS pay_zelle      text,
  ADD COLUMN IF NOT EXISTS pay_venmo      text,
  ADD COLUMN IF NOT EXISTS pay_cashapp    text,
  ADD COLUMN IF NOT EXISTS pay_paypal     text,
  ADD COLUMN IF NOT EXISTS pay_card_link  text,
  ADD COLUMN IF NOT EXISTS payment_notes  text,
  ADD COLUMN IF NOT EXISTS invoice_terms  text;

-- Card links get rendered as buttons on the invoice page, so only allow https.
ALTER TABLE public.mechanic_profiles
  DROP CONSTRAINT IF EXISTS mechanic_profiles_pay_card_link_https;
ALTER TABLE public.mechanic_profiles
  ADD CONSTRAINT mechanic_profiles_pay_card_link_https
  CHECK (pay_card_link IS NULL OR pay_card_link ~* '^https://');

ALTER TABLE public.mechanic_jobs
  ADD COLUMN IF NOT EXISTS invoice_token uuid NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS mechanic_jobs_invoice_token_key ON public.mechanic_jobs (invoice_token);

-- Private like every other bucket; the invoice function hands out short-lived signed URLs.
INSERT INTO storage.buckets (id, name, public)
VALUES ('shop-logos', 'shop-logos', false)
ON CONFLICT (id) DO NOTHING;

DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'Shop logo owner access'
  ) THEN
    CREATE POLICY "Shop logo owner access"
    ON storage.objects FOR ALL TO authenticated
    USING (bucket_id = 'shop-logos' AND (storage.foldername(name))[1] = auth.uid()::text)
    WITH CHECK (bucket_id = 'shop-logos' AND (storage.foldername(name))[1] = auth.uid()::text);
  END IF;
END; $$;
