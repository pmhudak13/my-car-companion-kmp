-- Labor guide, part 1: median hours other mechanics actually billed for a repair on similar vehicles.
-- Only the labor-guide edge function calls this (service role); users see the aggregate, never the rows.

CREATE OR REPLACE FUNCTION public.labor_guide_stats(p_make TEXT, p_model TEXT, p_year INT, p_repair TEXT)
RETURNS TABLE (median_hours NUMERIC, job_count INT)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT round(percentile_cont(0.5) WITHIN GROUP (ORDER BY li.quantity)::numeric, 1),
         count(DISTINCT li.mechanic_job_id)::int
    FROM mechanic_job_line_items li
    JOIN mechanic_jobs mj      ON mj.id = li.mechanic_job_id
    JOIN mechanic_profiles mp  ON mp.user_id = li.mechanic_user_id
   WHERE li.kind = 'labor'
     AND mj.status = 'completed'
     AND lower(mj.vehicle_make)  = lower(p_make)
     AND lower(mj.vehicle_model) = lower(p_model)
     AND mj.vehicle_year BETWEEN p_year - 3 AND p_year + 3
     -- ponytail: substring match on the description; add a repair taxonomy if lookups miss too often
     AND strpos(lower(li.description), lower(trim(p_repair))) > 0
     -- only hourly-billed lines, so quantity really is hours (a flat "1 x $450" line is not)
     AND mp.hourly_rate > 0
     AND abs(li.unit_price - mp.hourly_rate) <= mp.hourly_rate * 0.25;
$$;

REVOKE ALL ON FUNCTION public.labor_guide_stats(TEXT, TEXT, INT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.labor_guide_stats(TEXT, TEXT, INT, TEXT) TO service_role;
