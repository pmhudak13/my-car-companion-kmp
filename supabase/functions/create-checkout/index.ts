import Stripe from "https://esm.sh/stripe@14?target=deno";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-user-jwt",
};

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
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  try {
    // The Kotlin SDK may send the Authorization header with the anon key AND a second
    // Authorization header with the user JWT. Ktor joins multiple values with ", " so
    // the edge function sees "Bearer <anon>, Bearer <jwt>" — slice(7) gives a malformed
    // token and getUser() fails. We instead pass the user JWT in x-user-jwt to avoid
    // the duplicate-header problem entirely, with Authorization as a fallback.
    const userJwt = req.headers.get("x-user-jwt");
    const authHeader = req.headers.get("Authorization");
    const token = userJwt
      ?? (authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : authHeader ?? null);

    if (!token) {
      return new Response(JSON.stringify({ error: "Missing authorization" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    const {
      data: { user },
      error: authError,
    } = await supabase.auth.getUser(token);

    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { price_id, success_url, cancel_url } = await req.json();

    if (!price_id) {
      return new Response(JSON.stringify({ error: "price_id is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (!ALLOWED_PRICE_IDS.has(price_id)) {
      return new Response(JSON.stringify({ error: "Invalid price_id" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
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
      success_url: success_url ?? "https://www.mycarcompanion.org/app/?checkout=success",
      cancel_url: cancel_url ?? "https://www.mycarcompanion.org/app/",
      metadata: { supabase_user_id: user.id },
    });

    return new Response(JSON.stringify({ url: session.url }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("create-checkout error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      },
    );
  }
});
