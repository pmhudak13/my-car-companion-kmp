-- Addresses that unsubscribed. Service role only: RLS on, no policies.
create table if not exists public.email_suppressions (
  email text primary key check (email = lower(email)),
  created_at timestamptz not null default now()
);
alter table public.email_suppressions enable row level security;
revoke all on public.email_suppressions from anon, authenticated;
