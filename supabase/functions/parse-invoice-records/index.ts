import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const anthropicApiKey = Deno.env.get("ANTHROPIC_API_KEY") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

const VALID_CATEGORIES = [
  "Oil Change", "Tire Rotation", "Brake Service", "Air Filter", "Cabin Filter",
  "Transmission Service", "Coolant Flush", "Battery Replacement", "Wiper Blades",
  "Spark Plugs", "Alignment", "Suspension", "Registration", "Inspection",
  "Smog Check", "Body Work", "Other",
];

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse({ error: "Missing authorization header" }, 401);
  }

  const token = authHeader.slice(7);
  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) return jsonResponse({ error: "Unauthorized" }, 401);

  let body: { image_base64: string; mime_type: string; job_id: string };
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const { image_base64, mime_type, job_id } = body;
  if (!image_base64 || !mime_type || !job_id) {
    return jsonResponse({ error: "Missing required fields: image_base64, mime_type, job_id" }, 400);
  }

  // Verify the job belongs to this mechanic
  const { data: job, error: jobError } = await supabase
    .from("mechanic_jobs")
    .select("id, mechanic_user_id, vehicle_make, vehicle_model, vehicle_year")
    .eq("id", job_id)
    .eq("mechanic_user_id", user.id)
    .single();

  if (jobError || !job) return jsonResponse({ error: "Job not found or access denied" }, 403);

  const vehicleInfo = `${job.vehicle_year} ${job.vehicle_make} ${job.vehicle_model}`;

  // Build Claude request — supports images and PDFs
  const contentBlocks: unknown[] = [];

  if (mime_type === "application/pdf") {
    contentBlocks.push({
      type: "document",
      source: { type: "base64", media_type: "application/pdf", data: image_base64 },
    });
  } else {
    const supportedImageTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];
    const resolvedMime = supportedImageTypes.includes(mime_type) ? mime_type : "image/jpeg";
    contentBlocks.push({
      type: "image",
      source: { type: "base64", media_type: resolvedMime, data: image_base64 },
    });
  }

  contentBlocks.push({
    type: "text",
    text: `This is a service invoice or work order for a ${vehicleInfo}.

Extract all service records from this document and return a JSON array. Each record must have:
- "date": service date in YYYY-MM-DD format (use today if not shown: ${new Date().toISOString().slice(0, 10)})
- "category": exactly one of [${VALID_CATEGORIES.map((c) => `"${c}"`).join(", ")}]
- "description": brief description of the work done (required, non-empty)
- "mileage": integer odometer reading at service time (use 0 if not shown)
- "cost": total cost as a decimal number in USD (use null if not shown)
- "notes": any additional notes or part numbers (use null if none)

Return ONLY a valid JSON array with no extra text, markdown, or explanation.
Example: [{"date":"2024-01-15","category":"Oil Change","description":"Synthetic 5W-30 oil and filter","mileage":45000,"cost":89.99,"notes":"Mobil 1"}]`,
  });

  const claudeRes = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": anthropicApiKey,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-haiku-4-5-20251001",
      max_tokens: 2048,
      messages: [{ role: "user", content: contentBlocks }],
    }),
  });

  if (!claudeRes.ok) {
    const errText = await claudeRes.text();
    console.error("Claude API error:", errText);
    return jsonResponse({ error: "AI analysis failed. Please try again." }, 500);
  }

  const claudeData = await claudeRes.json();
  const rawText: string = claudeData?.content?.[0]?.text ?? "[]";

  // Strip any markdown code fences Claude might add despite instructions
  const cleaned = rawText
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/i, "")
    .trim();

  let records: unknown[];
  try {
    records = JSON.parse(cleaned);
    if (!Array.isArray(records)) records = [];
  } catch {
    console.error("Failed to parse Claude response:", cleaned);
    return jsonResponse({ error: "Could not parse AI response. Please try again with a clearer image." }, 500);
  }

  // Sanitize each record before returning
  const sanitized = records.map((r: unknown) => {
    const rec = r as Record<string, unknown>;
    const category = VALID_CATEGORIES.includes(rec.category as string)
      ? (rec.category as string)
      : "Other";
    return {
      date: typeof rec.date === "string" ? rec.date : new Date().toISOString().slice(0, 10),
      category,
      description: typeof rec.description === "string" ? rec.description : "",
      mileage: typeof rec.mileage === "number" ? Math.max(0, Math.round(rec.mileage)) : 0,
      cost: typeof rec.cost === "number" ? rec.cost : null,
      notes: typeof rec.notes === "string" ? rec.notes : null,
      isValid: typeof rec.description === "string" && rec.description.trim().length > 0,
    };
  }).filter((r) => r.description.trim().length > 0);

  return jsonResponse({ records: sanitized }, 200);
});
