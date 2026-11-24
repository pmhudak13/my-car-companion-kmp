-- Owners scan old invoices / service records (PDF or photo); AI turns them into maintenance logs.
-- Each scan is one row here: it meters the free monthly cap and holds the invoice as proof.

CREATE TABLE IF NOT EXISTS public.owner_invoice_scans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  vehicle_id UUID NOT NULL REFERENCES public.vehicles(id) ON DELETE CASCADE,
  -- set once the AI found records and the file was stored (in the private maintenance-attachments bucket)
  storage_path TEXT,
  file_name TEXT,
  mime_type TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS owner_invoice_scans_user_month_idx ON public.owner_invoice_scans (user_id, created_at);
ALTER TABLE public.owner_invoice_scans ENABLE ROW LEVEL SECURITY;
-- Rows are written by the parse-invoice-records function (service role) only.
-- The car's current owner sees them, so a buyer inherits the proof after a transfer.
CREATE POLICY "Vehicle owner reads invoice scans" ON public.owner_invoice_scans
  FOR SELECT USING (owns_vehicle(auth.uid(), vehicle_id));

ALTER TABLE public.maintenance_logs
  ADD COLUMN IF NOT EXISTS invoice_scan_id UUID REFERENCES public.owner_invoice_scans(id) ON DELETE SET NULL;

-- A record may only point at a scan of the same car, so "Invoice on file" can't be borrowed.
CREATE OR REPLACE FUNCTION public.check_invoice_scan_vehicle()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF NEW.invoice_scan_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM owner_invoice_scans s WHERE s.id = NEW.invoice_scan_id AND s.vehicle_id = NEW.vehicle_id
  ) THEN
    RAISE EXCEPTION 'Invoice scan belongs to a different vehicle';
  END IF;
  RETURN NEW;
END; $$;
REVOKE ALL ON FUNCTION public.check_invoice_scan_vehicle() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS trg_check_invoice_scan_vehicle ON public.maintenance_logs;
CREATE TRIGGER trg_check_invoice_scan_vehicle
  BEFORE INSERT OR UPDATE OF invoice_scan_id, vehicle_id ON public.maintenance_logs
  FOR EACH ROW EXECUTE FUNCTION public.check_invoice_scan_vehicle();

-- The current owner of the car can open the stored invoice (signed URL).
CREATE POLICY "Vehicle owner reads scanned invoices" ON storage.objects
  FOR SELECT USING (
    bucket_id = 'maintenance-attachments' AND EXISTS (
      SELECT 1 FROM public.owner_invoice_scans s
       WHERE s.storage_path = objects.name AND public.owns_vehicle(auth.uid(), s.vehicle_id)));
