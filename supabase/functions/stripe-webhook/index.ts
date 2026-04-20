import Stripe from "https://esm.sh/stripe@14?target=deno";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY") ?? "", {
  apiVersion: "2024-06-20",
  httpClient: Stripe.createFetchHttpClient(),
});

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const webhookSecret = Deno.env.get("STRIPE_WEBHOOK_SECRET") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

Deno.serve(async (req) => {
  const signature = req.headers.get("stripe-signature");
  if (!signature) {
    return new Response("Missing stripe-signature header", { status: 400 });
  }

  const body = await req.text();

  let event: Stripe.Event;
  try {
    event = await stripe.webhooks.constructEventAsync(body, signature, webhookSecret);
  } catch (err) {
    console.error("Webhook signature verification failed:", err);
    return new Response("Invalid signature", { status: 400 });
  }

  try {
    switch (event.type) {
      case "checkout.session.completed": {
        const session = event.data.object as Stripe.Checkout.Session;
        await handleCheckoutCompleted(session);
        break;
      }

      case "customer.subscription.updated": {
        const subscription = event.data.object as Stripe.Subscription;
        await handleSubscriptionUpdated(subscription);
        break;
      }

      case "customer.subscription.deleted": {
        const subscription = event.data.object as Stripe.Subscription;
        await handleSubscriptionDeleted(subscription);
        break;
      }

      case "invoice.payment_failed": {
        const invoice = event.data.object as Stripe.Invoice;
        if (invoice.subscription) {
          await handlePaymentFailed(invoice.subscription as string);
        }
        break;
      }

      default:
        // Unhandled event — not an error, just ignore
        break;
    }

    return new Response(JSON.stringify({ received: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Webhook handler error:", err);
    return new Response("Internal server error", { status: 500 });
  }
});

async function handleCheckoutCompleted(session: Stripe.Checkout.Session) {
  if (session.mode !== "subscription") return;

  const userId = session.metadata?.supabase_user_id;
  if (!userId) return;

  // Determine tier from the price purchased
  const lineItems = await stripe.checkout.sessions.listLineItems(session.id);
  const priceId = lineItems.data[0]?.price?.id;
  const tier = tierFromPriceId(priceId);
  if (!tier) {
    console.error("handleCheckoutCompleted: unknown priceId, aborting profile update", priceId);
    return;
  }

  // Single update merges all fields to avoid a partial-update race window
  await supabase.from("profiles").update({
    is_premium: true,
    is_mechanic_pro: tier === "mechanic_pro",
    subscription_tier: tier,
    stripe_customer_id: session.customer as string,
  }).eq("user_id", userId);
}

async function handleSubscriptionUpdated(subscription: Stripe.Subscription) {
  const customerId = subscription.customer as string;
  const priceId = subscription.items.data[0]?.price.id;
  const tier = tierFromPriceId(priceId);
  const isActive = subscription.status === "active" || subscription.status === "trialing";

  // Always set is_mechanic_pro in the same update to avoid partial-state windows
  // and to correctly revoke it on downgrades (e.g. mechanic_pro → premium)
  await supabase.from("profiles").update({
    is_premium: isActive,
    is_mechanic_pro: tier === "mechanic_pro" ? isActive : false,
    subscription_tier: isActive && tier ? tier : "free",
  }).eq("stripe_customer_id", customerId);
}

async function handleSubscriptionDeleted(subscription: Stripe.Subscription) {
  const customerId = subscription.customer as string;
  await supabase.from("profiles").update({
    is_premium: false,
    is_mechanic_pro: false,
    subscription_tier: "free",
  }).eq("stripe_customer_id", customerId);
}

async function handlePaymentFailed(subscriptionId: string) {
  const subscription = await stripe.subscriptions.retrieve(subscriptionId);
  const customerId = subscription.customer as string;
  // Mark as inactive but keep customer ID; they may update their payment method
  await supabase.from("profiles").update({
    is_premium: false,
    is_mechanic_pro: false,
    subscription_tier: "past_due",
  }).eq("stripe_customer_id", customerId);
}

/** Maps a Stripe Price ID to the internal subscription_tier string, or null for unknown prices. */
function tierFromPriceId(priceId: string | undefined): string | null {
  // Premium (car owner) — monthly + yearly
  if (
    priceId === "price_1TFf16EEo6NSMXCB9SQ29WiZ" ||
    priceId === "price_1TFfCmEEo6NSMXCBhvpPp5ut"
  ) {
    return "premium";
  }
  // Mechanic Pro — monthly + yearly
  if (
    priceId === "price_1TFfAGEEo6NSMXCBu8hSzWVq" ||
    priceId === "price_1TFfCIEEo6NSMXCBXTQaAI5x"
  ) {
    return "mechanic_pro";
  }
  console.warn("Unknown price ID encountered:", priceId);
  return null;
}
