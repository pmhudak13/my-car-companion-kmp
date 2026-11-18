-- Admin "Make Mechanic" inserts a mechanic_profiles row for another user; the only INSERT policy was self-only (403).
create policy "Admins can insert mechanic profiles"
  on public.mechanic_profiles
  for insert
  to authenticated
  with check (public.has_role(auth.uid(), 'admin'::app_role));
