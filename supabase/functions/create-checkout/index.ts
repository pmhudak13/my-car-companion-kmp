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

// Ktor 3.x on wasmJs is strict about Content-Length matching. The Supabase relay
// can compress responses and alter the byte count, causing an IllegalStateException.
// Explicitly setting Content-Encoding: identity disables relay compression and
// setting Content-Length to the exact UTF-8 byte count prevents the mismatch.
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

// Hardcoded price IDs — must match SubscriptionRepository.kt and stripe-webhook/index.ts
const ALLOWED_PRICE_IDS = new Set([
  "price_1TFf16EEo6NSMXCB9SQ29WiZ", // Premium monthly  $4.99/mo
  "price_1TFfCmEEo6NSMXCBhvpPp5ut", // Premium yearly   $49.99/yr
  "price_1TFfAGEEo6NSMXCBu8hSzWVq", // Mechanic monthly $14.99/mo
  "price_1TFfCIEEo6NSMXCBXTQaAI5x", // Mechanic yearly  $149.99/yr
]);

Deno.serve(async (req) => {
  const cors = corsHeaders(req);

  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: cors });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: cors });
  }

  try {
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

    const {
      data: { user },
      error: authError,
    } = await supabase.auth.getUser(token);

    if (authError || !user) {
      return jsonResponse(cors, { error: "Unauthorized" }, 401);
    }

    const { price_id, success_url, cancel_url } = await req.json();

    if (!price_id) {
      return jsonResponse(cors, { error: "price_id is required" }, 400);
    }

    if (!ALLOWED_PRICE_IDS.has(price_id)) {
      return jsonResponse(cors, { error: "Invalid price_id" }, 400);
    }

    // Look up or create a Stripe customer for this user
    const { data: profile } = await supabase
      .from("profiles")
      .select("stripe_customer_id, email")
      .eq("user_id", user.id)
      .single();

    let customerId: string | undefined = profile?.stripe_customer_id;

    if (!customerId) {
      const customer = await stripe.customers.create({
        email: user.email ?? profile?.email ?? undefined,
        metadata: { supabase_user_id: user.id },
      });
      customerId = customer.id;

      await supabase
        .from("profiles")
        .update({ stripe_customer_id: customerId })
        .eq("user_id", user.id);
    }

    const session = await stripe.checkout.sessions.create({
      customer: customerId,
      mode: "subscription",
      line_items: [{ price: price_id, quantity: 1 }],
      success_url: success_url ?? "https://www.mycarcompanion.org/webapp/?checkout=success",
      cancel_url: cancel_url ?? "https://www.mycarcompanion.org/webapp/",
      metadata: { supabase_user_id: user.id },
    });

    return jsonResponse(cors, { url: session.url });
  } catch (err) {
    console.error("create-checkout error:", err);
    return jsonResponse(cors, { error: "Internal server error" }, 500);
  }
});
