import Stripe from "https://esm.sh/stripe@14?target=deno";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY") ?? "", {
  apiVersion: "2024-06-20",
  httpClient: Stripe.createFetchHttpClient(),
});

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const ALLOWED_PRICE_IDS = new Set([
  Deno.env.get("STRIPE_PRICE_PREMIUM_MONTHLY") ?? "",
  Deno.env.get("STRIPE_PRICE_PREMIUM_YEARLY") ?? "",
  Deno.env.get("STRIPE_PRICE_MECHANIC_MONTHLY") ?? "",
  Deno.env.get("STRIPE_PRICE_MECHANIC_YEARLY") ?? "",
].filter(Boolean));

Deno.serve(async (req) => {
  // Only allow POST
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    // Authenticate the calling user via their JWT
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "Missing authorization" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const {
      data: { user },
      error: authError,
    } = await supabase.auth.getUser();

    if (authError || !user) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Parse request body
    const { price_id, success_url, cancel_url } = await req.json();

    if (!price_id) {
      return new Response(JSON.stringify({ error: "price_id is required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (!ALLOWED_PRICE_IDS.has(price_id)) {
      return new Response(JSON.stringify({ error: "Invalid price_id" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
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

      // Persist the customer ID so we reuse it on future checkouts
      await supabase
        .from("profiles")
        .update({ stripe_customer_id: customerId })
        .eq("user_id", user.id);
    }

    // Create a Stripe Checkout Session for a subscription
    const session = await stripe.checkout.sessions.create({
      customer: customerId,
      mode: "subscription",
      line_items: [{ price: price_id, quantity: 1 }],
      // Mobile deep-link redirect URLs
      success_url:
        success_url ?? "org.mycarcompanion.app://subscription/success?session_id={CHECKOUT_SESSION_ID}",
      cancel_url:
        cancel_url ?? "org.mycarcompanion.app://subscription/cancel",
      // Prefill email so the user doesn't have to type it
      customer_email: customerId ? undefined : (user.email ?? undefined),
      metadata: { supabase_user_id: user.id },
    });

    return new Response(JSON.stringify({ url: session.url }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("create-checkout-session error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }
});
