-- Both are trigger functions (return type trigger; fired by trg_link_job_to_customer_vehicle on
-- mechanic_jobs and trg_link_vehicle_to_mechanic_jobs on vehicles). Triggers do not need the
-- caller to hold EXECUTE, so nobody should be able to call them directly through the API.
revoke execute on function public.link_job_to_customer_vehicle() from public, anon, authenticated;
revoke execute on function public.link_vehicle_to_mechanic_jobs() from public, anon, authenticated;
