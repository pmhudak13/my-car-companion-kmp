import Stripe from "https://esm.sh/stripe@14?target=deno";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ALLOWED_ORIGINS = new Set([
  "https://www.mycarcompanion.org",
  "https://mycarcompanion.org",
]);

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get("Origin") ?? "";
  return {
    "Access-Control-Allow-Origin": ALLOWED_ORIGINS.has(origin)
      ? origin
      : "https://www.mycarcompanion.org",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-user-jwt, x-region",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  };
}

function jsonResponse(cors: Record<string, string>, body: object, status = 200): Response {
  const text = JSON.stringify(body);
  const bytes = new TextEncoder().encode(text);
  return new Response(bytes, {
    status,
    headers: {
      ...cors,
      "Content-Type": "application/json",
      "Content-Encoding": "identity",
      "Content-Length": bytes.byteLength.toString(),
    },
  });
}

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY") ?? "", {
  apiVersion: "2024-06-20",
  httpClient: Stripe.createFetchHttpClient(),
});

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

Deno.serve(async (req) => {
  const cors = corsHeaders(req);

  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: cors });
  }

  // The supabase-kt SDK automatically sends Authorization: Bearer <session-token>.
  // We also accept x-user-jwt as a fallback for older clients that sent both headers.
  const authHeader = req.headers.get("Authorization");
  const userJwt = req.headers.get("x-user-jwt");
  const token = (authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null)
    ?? userJwt
    ?? null;

  if (!token) {
    return jsonResponse(cors, { error: "Missing authorization" }, 401);
  }

  const supabase = createClient(supabaseUrl, supabaseServiceKey);

  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) {
    return jsonResponse(cors, { error: "Unauthorized" }, 401);
  }

  const { data: profile } = await supabase
    .from("profiles")
    .select("stripe_customer_id")
    .eq("user_id", user.id)
    .single();

  if (!profile?.stripe_customer_id) {
    return jsonResponse(cors, { error: "No active subscription found" }, 404);
  }

  // Allow caller to specify return URL so both mobile and web can use this function
  const body = req.headers.get("content-type")?.includes("application/json")
    ? await req.json().catch(() => ({}))
    : {};
  const returnUrl: string = body?.return_url ?? "https://www.mycarcompanion.org/webapp/";

  try {
    const session = await stripe.billingPortal.sessions.create({
      customer: profile.stripe_customer_id,
      return_url: returnUrl,
    });

    return jsonResponse(cors, { url: session.url });
  } catch (err) {
    console.error("create-portal error:", err);
    return jsonResponse(cors, { error: "Failed to create portal session" }, 500);
  }
});
