import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const anthropicApiKey = Deno.env.get("ANTHROPIC_API_KEY") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

// Mechanics' real billed hours win once this many completed jobs agree; below it, ask Claude.
const MIN_JOBS = 3;

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

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse({ error: "Missing authorization header" }, 401);
  }
  const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader.slice(7));
  if (authError || !user) return jsonResponse({ error: "Unauthorized" }, 401);

  // ponytail: mechanics only, no per-user rate limit; add one if AI spend shows abuse
  const { data: profile } = await supabase
    .from("mechanic_profiles").select("id").eq("user_id", user.id).maybeSingle();
  if (!profile) return jsonResponse({ error: "Labor guide is for mechanics" }, 403);

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

  const claudeRes = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": anthropicApiKey,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-haiku-4-5-20251001",
      max_tokens: 200,
      // ponytail: temperature 0 keeps repeat lookups steady; cache answers in a table if they still drift
      temperature: 0,
      messages: [{
        role: "user",
        content: `You are an automotive flat-rate labor guide. Estimate the standard labor time a professional technician would bill for this repair.

Vehicle: ${year} ${make} ${model}
Repair: ${repair}

Return ONLY JSON, no markdown: {"hours": <number, one decimal>, "note": "<one short sentence on what the time covers>"}`,
      }],
    }),
  });

  if (!claudeRes.ok) {
    console.error("Claude API error:", await claudeRes.text());
    return jsonResponse({ error: "Labor lookup failed. Please try again." }, 500);
  }

  const raw: string = (await claudeRes.json())?.content?.[0]?.text ?? "";
  let parsed: { hours?: unknown; note?: unknown } = {};
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

  return jsonResponse({
    hours: Math.round(hours * 10) / 10,
    source: "ai",
    job_count: stats?.job_count ?? 0,
    note: text(parsed.note) || null,
  }, 200);
});
