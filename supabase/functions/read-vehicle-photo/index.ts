// Reads a license plate from a photo so mechanics don't type it. VINs come from the barcode scanner,
// where the check digit catches misreads. Mechanics and admins only: every call is a paid AI request.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(Deno.env.get("SUPABASE_URL") ?? "", Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "");
// the project secret was saved as "Anthropic_API_Key"; accept either spelling
const anthropicApiKey = Deno.env.get("ANTHROPIC_API_KEY") || Deno.env.get("Anthropic_API_Key") || "";

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

  const { data: roles } = await supabase
    .from("user_roles").select("role").eq("user_id", user.id).in("role", ["mechanic", "admin"]);
  if (!roles?.length) return jsonResponse({ error: "Photo reading is for mechanics." }, 403);

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
      model: "claude-haiku-5-5",
      // thinking counts toward max_tokens; low effort keeps it short for a one-line answer
      max_tokens: 1024,
      output_config: { effort: "low" },
      messages: [{
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mime_type, data: image_base64 } },
          {
            type: "text",
            text: `Read the vehicle license plate in this photo.
Return ONLY JSON: {"plate": "<plate characters, no spaces or dashes, or null>"}.
Ignore state names, slogans, dealer frames, registration stickers and any VIN. Use null if you can't read a plate with confidence.`,
          },
        ],
      }],
    }),
  });
  if (!claudeRes.ok) {
    console.error("Claude API error:", await claudeRes.text());
    return jsonResponse({ error: "Couldn't read that photo. Please try again." }, 500);
  }

  // the reply can start with thinking blocks, so take the text block
  const raw: string = (await claudeRes.json())?.content?.find((b: { type: string }) => b.type === "text")?.text ?? "{}";
  let parsed: { plate?: unknown };
  try {
    parsed = JSON.parse(raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim());
  } catch {
    console.error("Unparseable AI reply:", raw);
    return jsonResponse({ error: "Couldn't read a plate in that photo. Try a closer, sharper shot." }, 200);
  }

  const plate = typeof parsed.plate === "string" ? parsed.plate.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 10) : "";
  return jsonResponse({ plate: plate || null }, 200);
});
