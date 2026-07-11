-- Cost of service + payment received tracking on mechanic jobs
alter table public.mechanic_jobs
  add column if not exists total_cost numeric,
  add column if not exists payment_received boolean not null default false;
