import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Turns a service invoice (photo or PDF) into maintenance records with AI.
//   { job_id }     mechanic importing a customer's history onto a job (unchanged behaviour)
//   { vehicle_id } owner scanning their own old invoices: free cap per month unless Premium, and the
//                  file is kept privately as proof on the records it created.

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
// the project secret was saved as "Anthropic_API_Key"; accept either spelling
const anthropicApiKey = Deno.env.get("ANTHROPIC_API_KEY") || Deno.env.get("Anthropic_API_Key") || "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

const OWNER_FREE_SCANS_PER_MONTH = 10;
// ~7.5 MB file; Claude takes PDFs up to 32 MB but edge function memory is the tighter limit
const MAX_BASE64 = 10 * 1024 * 1024;
const MAX_IMAGE_BASE64 = Math.ceil((5 * 1024 * 1024 * 4) / 3);
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];

const VALID_CATEGORIES = [
  "Oil Change", "Tire Rotation", "Brake Service", "Air Filter", "Cabin Filter",
  "Transmission Service", "Coolant Flush", "Battery Replacement", "Wiper Blades",
  "Spark Plugs", "Alignment", "Suspension", "Registration", "Inspection",
  "Smog Check", "Body Work", "Other",
];

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

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
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

  let body: { image_base64?: string; mime_type?: string; job_id?: string; vehicle_id?: string; file_name?: string };
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const { image_base64, mime_type, job_id, vehicle_id } = body;
  if (!image_base64 || !mime_type || (!job_id && !vehicle_id)) {
    return jsonResponse({ error: "Missing required fields: image_base64, mime_type, and job_id or vehicle_id" }, 400);
  }
  if (mime_type !== "application/pdf" && !IMAGE_TYPES.includes(mime_type)) {
    return jsonResponse({ error: "Use a PDF or a JPEG, PNG, GIF or WebP photo." }, 400);
  }
  if (image_base64.length > (mime_type === "application/pdf" ? MAX_BASE64 : MAX_IMAGE_BASE64)) {
    return jsonResponse({ error: "That file is too large. Use a PDF under 7 MB or a photo under 5 MB." }, 400);
  }

  let vehicleInfo: string;
  let scanId: string | null = null;

  if (job_id) {
    // Mechanic: the job must be theirs
    const { data: job, error: jobError } = await supabase
      .from("mechanic_jobs")
      .select("id, mechanic_user_id, vehicle_make, vehicle_model, vehicle_year")
      .eq("id", job_id)
      .eq("mechanic_user_id", user.id)
      .single();
    if (jobError || !job) return jsonResponse({ error: "Job not found or access denied" }, 403);
    vehicleInfo = `${job.vehicle_year} ${job.vehicle_make} ${job.vehicle_model}`;
  } else {
    // Owner: the car must be theirs, and free accounts get a monthly cap
    const { data: vehicle } = await supabase
      .from("vehicles").select("id, owner_id, make, model, year")
      .eq("id", vehicle_id).eq("owner_id", user.id).maybeSingle();
    if (!vehicle) return jsonResponse({ error: "Car not found or access denied" }, 403);
    vehicleInfo = `${vehicle.year} ${vehicle.make} ${vehicle.model}`;

    const { data: profile } = await supabase.from("profiles").select("is_premium").eq("user_id", user.id).maybeSingle();
    if (!profile?.is_premium) {
      const monthStart = new Date();
      monthStart.setUTCDate(1);
      monthStart.setUTCHours(0, 0, 0, 0);
      const { count } = await supabase.from("owner_invoice_scans")
        .select("id", { count: "exact", head: true })
        .eq("user_id", user.id).gte("created_at", monthStart.toISOString());
      if ((count ?? 0) >= OWNER_FREE_SCANS_PER_MONTH) {
        return jsonResponse({
          error: `You've used your ${OWNER_FREE_SCANS_PER_MONTH} free scans this month. Upgrade to Premium for unlimited scans, or add records by hand.`,
          limit_reached: true,
        }, 200);
      }
    }
    if (!anthropicApiKey) {
      return jsonResponse({ error: "Invoice scanning isn't available right now. Add the record by hand instead." }, 200);
    }
    // Counted before the AI call: a failed read still cost money
    const { data: scan, error: scanError } = await supabase.from("owner_invoice_scans")
      .insert({ user_id: user.id, vehicle_id, file_name: body.file_name?.slice(0, 200) ?? null, mime_type })
      .select("id").single();
    if (scanError || !scan) {
      console.error("Scan insert failed:", scanError);
      return jsonResponse({ error: "Could not start the scan. Please try again." }, 500);
    }
    scanId = scan.id;
  }

  // No key set = AI scan off (no API budget yet); setting ANTHROPIC_API_KEY turns it back on
  if (!anthropicApiKey) {
    return jsonResponse({ error: "Invoice scanning isn't available right now. Use CSV import instead." }, 200);
  }

  const contentBlocks: unknown[] = [
    mime_type === "application/pdf"
      ? { type: "document", source: { type: "base64", media_type: "application/pdf", data: image_base64 } }
      : { type: "image", source: { type: "base64", media_type: mime_type, data: image_base64 } },
    {
      type: "text",
      text: `This is a service invoice, work order or maintenance record for a ${vehicleInfo}. It may have several pages or visits.

Return ONLY a JSON object, no extra text: {"vin": <the 17-character VIN if printed on it, else null>, "records": [...]}.
Each record is one service performed, with:
- "date": service date in YYYY-MM-DD format (use today if not shown: ${new Date().toISOString().slice(0, 10)})
- "category": exactly one of [${VALID_CATEGORIES.map((c) => `"${c}"`).join(", ")}]
- "description": brief description of the work done (required, non-empty)
- "mileage": integer odometer reading at service time (use 0 if not shown)
- "cost": total cost as a decimal number in USD (use null if not shown)
- "notes": any additional notes or part numbers (use null if none)

Example: {"vin":null,"records":[{"date":"2024-01-15","category":"Oil Change","description":"Synthetic 5W-30 oil and filter","mileage":45000,"cost":89.99,"notes":"Mobil 1"}]}`,
    },
  ];

  const claudeRes = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": anthropicApiKey,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: "claude-haiku-4-5-20251001",
      max_tokens: 4096,
      messages: [{ role: "user", content: contentBlocks }],
    }),
  });

  if (!claudeRes.ok) {
    console.error("Claude API error:", await claudeRes.text());
    return jsonResponse({ error: "AI analysis failed. Please try again." }, 500);
  }

  const claudeData = await claudeRes.json();
  const rawText: string = claudeData?.content?.[0]?.text ?? "{}";
  const cleaned = rawText.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim();

  let records: unknown[];
  let vin: string | null = null;
  try {
    const parsed = JSON.parse(cleaned);
    // older prompt answered with a bare array; accept both
    records = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.records) ? parsed.records : [];
    const v = typeof parsed?.vin === "string" ? parsed.vin.toUpperCase().replace(/[^A-HJ-NPR-Z0-9]/g, "") : "";
    vin = v.length === 17 ? v : null;
  } catch {
    console.error("Failed to parse Claude response:", cleaned);
    return jsonResponse({ error: "Could not parse AI response. Please try again with a clearer image." }, 500);
  }

  const sanitized = records.map((r: unknown) => {
    const rec = r as Record<string, unknown>;
    const category = VALID_CATEGORIES.includes(rec.category as string) ? (rec.category as string) : "Other";
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

  // Owner: keep the invoice as proof, but only when it actually produced records
  if (scanId && sanitized.length > 0) {
    const ext = mime_type === "application/pdf" ? "pdf" : mime_type.split("/")[1];
    const path = `${user.id}/invoice-scans/${vehicle_id}/${scanId}.${ext}`;
    const { error: upErr } = await supabase.storage.from("maintenance-attachments")
      .upload(path, base64ToBytes(image_base64), { contentType: mime_type, upsert: false });
    if (upErr) console.error("Invoice store failed (records still returned):", upErr);
    else await supabase.from("owner_invoice_scans").update({ storage_path: path }).eq("id", scanId);
  }

  return jsonResponse({ records: sanitized, vin, scan_id: scanId }, 200);
});
