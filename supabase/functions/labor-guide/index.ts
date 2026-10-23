import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const anthropicApiKey = Deno.env.get("ANTHROPIC_API_KEY") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

// Mechanics' real billed hours win once this many completed jobs agree; below it, ask Claude.
const MIN_JOBS = 3;
// Fresh Claude calls per paying user (Mechanic Pro or Premium) per calendar month (UTC); cached answers don't count.
const MONTHLY_AI_LIMIT = 300;

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-region",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

const text = (v: unknown) => (typeof v === "string" ? v.trim() : "");

type Part = { name: string; low: number; high: number };

// Claude's parts list is untrusted model output: keep only well-formed rows, at most 8.
function cleanParts(v: unknown): Part[] {
  if (!Array.isArray(v)) return [];
  return v.flatMap((p) => {
    const name = text(p?.name).slice(0, 80);
    const low = Number(p?.low), high = Number(p?.high);
    if (!name || !Number.isFinite(low) || !Number.isFinite(high) || low < 0 || high < low || high > 20000) return [];
    return [{ name, low: Math.round(low), high: Math.round(high) }];
  }).slice(0, 8);
}

const upgradeResponse = (isMechanic: boolean) => jsonResponse({
  error: isMechanic
    ? "Not enough job data for this car yet. AI estimates with hours and parts come with Mechanic Pro."
    : "Not enough job data for this car yet. AI fair-price checks come with Premium.",
  upgrade: true,
}, 200);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse({ error: "Missing authorization header" }, 401);
  }
  const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader.slice(7));
  if (authError || !user) return jsonResponse({ error: "Unauthorized" }, 401);

  // Mechanics use it to quote; car owners use it to check a quote (the fair-price check)
  const { data: profile } = await supabase
    .from("mechanic_profiles").select("id").eq("user_id", user.id).maybeSingle();
  const isMechanic = !!profile;

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const year = Number(body.year);
  const make = text(body.make);
  const model = text(body.model);
  const repair = text(body.repair);
  if (!Number.isInteger(year) || year < 1900 || year > 2100 || !make || !model || !repair ||
      make.length > 60 || model.length > 60 || repair.length > 120) {
    return jsonResponse({ error: "Year, make, model, and repair are required" }, 400);
  }

  const { data: stats, error: statsError } = await supabase
    .rpc("labor_guide_stats", { p_make: make, p_model: model, p_year: year, p_repair: repair })
    .single();
  if (statsError) console.error("labor_guide_stats failed:", statsError.message);
  if (stats && stats.job_count >= MIN_JOBS) {
    return jsonResponse({ hours: Number(stats.median_hours), source: "mechanics", job_count: stats.job_count }, 200);
  }

  // AI estimates are paid: Mechanic Pro for mechanics, Premium for owners; both flags are set only by the billing functions
  const { data: billing } = await supabase
    .from("profiles").select("is_mechanic_pro, is_premium").eq("user_id", user.id).maybeSingle();
  if (!(isMechanic ? billing?.is_mechanic_pro : billing?.is_premium)) return upgradeResponse(isMechanic);

  // No key set = AI fallback off; setting ANTHROPIC_API_KEY turns it on for Mechanic Pro
  if (!anthropicApiKey) {
    return jsonResponse({ error: "No labor data for this car yet. Enter the hours manually." }, 200);
  }

  const cacheKey = [year, make, model, repair].join("|").toLowerCase().replace(/\s+/g, " ");
  const { data: cached } = await supabase
    .from("labor_guide_ai_cache").select("hours, note, parts").eq("cache_key", cacheKey).maybeSingle();
  if (cached) {
    return jsonResponse({
      hours: Number(cached.hours), source: "ai", job_count: stats?.job_count ?? 0,
      note: cached.note, parts: cached.parts ?? [],
    }, 200);
  }

  const monthStart = new Date();
  monthStart.setUTCDate(1);
  monthStart.setUTCHours(0, 0, 0, 0);
  const { count: usedThisMonth, error: countError } = await supabase
    .from("labor_guide_ai_lookups").select("id", { count: "exact", head: true })
    .eq("user_id", user.id).gte("created_at", monthStart.toISOString());
  if (countError) {
    console.error("labor_guide_ai_lookups count failed:", countError.message);
    return jsonResponse({ error: "Labor lookup failed. Please try again." }, 500);
  }
  if ((usedThisMonth ?? 0) >= MONTHLY_AI_LIMIT) {
    return jsonResponse({
      error: `You've used all ${MONTHLY_AI_LIMIT} AI lookups this month. Times you've looked up before still work.`,
    }, 200);
  }

  const claudeRes = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": anthropicApiKey,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-haiku-4-5-20251001",
      max_tokens: 600,
      // ponytail: temperature 0 keeps repeat lookups steady; cache answers in a table if they still drift
      temperature: 0,
      messages: [{
        role: "user",
        content: `You are an automotive flat-rate labor guide. Estimate the standard labor time a professional technician would bill for this repair, and the parts it usually needs.

Vehicle: ${year} ${make} ${model}
Repair: ${repair}

Return ONLY JSON, no markdown: {"hours": <number, one decimal>, "note": "<one short sentence on what the time covers>", "parts": [{"name": "<part>", "low": <typical US aftermarket price in dollars>, "high": <typical OEM price in dollars>}]}
List at most 6 parts, each one the shop would buy for this job. Use an empty list if the repair needs no parts.`,
      }],
    }),
  });

  if (!claudeRes.ok) {
    console.error("Claude API error:", await claudeRes.text());
    return jsonResponse({ error: "Labor lookup failed. Please try again." }, 500);
  }

  const raw: string = (await claudeRes.json())?.content?.[0]?.text ?? "";
  // the call cost money whether or not the answer parses, so it counts toward the cap
  const { error: logError } = await supabase.from("labor_guide_ai_lookups").insert({ user_id: user.id });
  if (logError) console.error("labor_guide_ai_lookups insert failed:", logError.message);

  let parsed: { hours?: unknown; note?: unknown; parts?: unknown } = {};
  try {
    parsed = JSON.parse(raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim());
  } catch {
    console.error("Failed to parse Claude response:", raw);
  }
  const hours = Number(parsed.hours);
  // 200, not 4xx: "no answer" is a normal outcome, and supabase-kt throws away the body on non-2xx
  if (!Number.isFinite(hours) || hours <= 0 || hours > 60) {
    return jsonResponse({ error: "No labor time found for that repair. Try wording it differently." }, 200);
  }

  const answer = {
    hours: Math.round(hours * 10) / 10,
    note: text(parsed.note).slice(0, 300) || null,
    parts: cleanParts(parsed.parts),
  };
  const { error: cacheError } = await supabase
    .from("labor_guide_ai_cache").upsert({ cache_key: cacheKey, ...answer }, { ignoreDuplicates: true });
  if (cacheError) console.error("labor_guide_ai_cache insert failed:", cacheError.message);

  return jsonResponse({ ...answer, source: "ai", job_count: stats?.job_count ?? 0 }, 200);
});
