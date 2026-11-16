-- Reviews must come from a real customer: someone whose vehicle the mechanic was assigned to
-- (active or completed) or worked on (a mechanic_jobs row linked to their vehicle). No self-reviews.
create or replace function public.can_review_mechanic(p_mechanic uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() is not null
    and auth.uid() <> p_mechanic
    and (
      exists (
        select 1 from mechanic_assignments a
        join vehicles v on v.id = a.vehicle_id
        where a.mechanic_user_id = p_mechanic
          and v.owner_id = auth.uid()
          and a.status in ('active', 'completed')
      )
      or exists (
        select 1 from mechanic_jobs j
        join vehicles v on v.id = j.vehicle_id
        where j.mechanic_user_id = p_mechanic
          and v.owner_id = auth.uid()
      )
    );
$$;
revoke execute on function public.can_review_mechanic(uuid) from public, anon;
grant execute on function public.can_review_mechanic(uuid) to authenticated;

drop policy if exists "Users can create reviews" on public.mechanic_reviews;
drop policy if exists "Users can insert their own reviews" on public.mechanic_reviews;
create policy "Customers can review their mechanics" on public.mechanic_reviews
  for insert to authenticated
  with check (
    reviewer_user_id = auth.uid()
    and reviewer_user_id <> mechanic_user_id
    and public.can_review_mechanic(mechanic_user_id)
  );

-- The old update policy had no WITH CHECK, so a review could be re-pointed at any mechanic.
alter policy "Users can update their own reviews" on public.mechanic_reviews
  with check (
    reviewer_user_id = auth.uid()
    and reviewer_user_id <> mechanic_user_id
    and public.can_review_mechanic(mechanic_user_id)
  );
