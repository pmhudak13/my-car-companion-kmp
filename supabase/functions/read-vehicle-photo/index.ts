// Reads a license plate and/or VIN from one photo so mechanics don't type them.
// Mechanics only: every call is a paid AI request.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(Deno.env.get("SUPABASE_URL") ?? "", Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "");
const anthropicApiKey = Deno.env.get("ANTHROPIC_API_KEY") ?? "";

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-region",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...corsHeaders } });
}

const IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];
// base64 of the 5 MB Claude image limit
const MAX_BASE64 = Math.ceil((5 * 1024 * 1024 * 4) / 3);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) return jsonResponse({ error: "Missing authorization header" }, 401);
  const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader.slice(7));
  if (authError || !user) return jsonResponse({ error: "Unauthorized" }, 401);

  const { data: mechanicRole } = await supabase
    .from("user_roles").select("role").eq("user_id", user.id).eq("role", "mechanic").maybeSingle();
  if (!mechanicRole) return jsonResponse({ error: "Photo reading is for mechanics." }, 403);

  let body: { image_base64?: string; mime_type?: string };
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }
  const { image_base64, mime_type } = body;
  if (!image_base64 || !mime_type) return jsonResponse({ error: "Missing image_base64 or mime_type" }, 400);
  if (!IMAGE_TYPES.includes(mime_type)) return jsonResponse({ error: "Photo must be a JPEG, PNG, GIF or WebP image." }, 400);
  if (image_base64.length > MAX_BASE64) return jsonResponse({ error: "Photo is too large. Use a smaller photo (under 5 MB)." }, 400);

  if (!anthropicApiKey) return jsonResponse({ error: "Photo reading isn't available right now. Type it in instead." }, 200);

  const claudeRes = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": anthropicApiKey, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({
      model: "claude-haiku-4-5-20251001",
      max_tokens: 200,
      messages: [{
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mime_type, data: image_base64 } },
          {
            type: "text",
            text: `This photo shows a vehicle's license plate, VIN plate/sticker, or both.
Return ONLY JSON: {"plate": "<plate characters, no spaces or dashes, or null>", "vin": "<17-character VIN, or null>"}.
Ignore state names, slogans, dealer frames and registration stickers. Use null for anything you can't read with confidence.`,
          },
        ],
      }],
    }),
  });
  if (!claudeRes.ok) {
    console.error("Claude API error:", await claudeRes.text());
    return jsonResponse({ error: "Couldn't read that photo. Please try again." }, 500);
  }

  const raw: string = (await claudeRes.json())?.content?.[0]?.text ?? "{}";
  let parsed: { plate?: unknown; vin?: unknown };
  try {
    parsed = JSON.parse(raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim());
  } catch {
    console.error("Unparseable AI reply:", raw);
    return jsonResponse({ error: "Couldn't read a plate or VIN in that photo. Try a closer, sharper shot." }, 200);
  }

  const plate = typeof parsed.plate === "string" ? parsed.plate.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 10) : "";
  const vin = typeof parsed.vin === "string" ? parsed.vin.toUpperCase().replace(/[^A-HJ-NPR-Z0-9]/g, "") : "";
  return jsonResponse({ plate: plate || null, vin: vin.length === 17 ? vin : null }, 200);
});
