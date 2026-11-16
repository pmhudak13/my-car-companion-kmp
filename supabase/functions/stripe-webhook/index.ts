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

      case "invoice.payment_succeeded": {
        const invoice = event.data.object as Stripe.Invoice;
        if (invoice.subscription && invoice.billing_reason !== "subscription_create") {
          // subscription_create is already handled by checkout.session.completed.
          // This covers renewals and payment lapse recovery (card updated after failure).
          await handlePaymentSucceeded(invoice.subscription as string);
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

  // A failed confirmation email must not make Stripe retry the whole event.
  try {
    await sendSubscriptionConfirmation(session, tier, lineItems.data[0]?.price);
  } catch (err) {
    console.error("Subscription confirmation email failed:", err);
  }
}

// California auto-renewal law: confirm plan, price, renewal terms, and how to cancel after sign-up.
async function sendSubscriptionConfirmation(session: Stripe.Checkout.Session, tier: string, price?: Stripe.Price | null) {
  const to = session.customer_details?.email;
  const resendKey = Deno.env.get("RESEND_API_KEY");
  if (!to || !resendKey) {
    console.warn("Skipping subscription confirmation: missing email or RESEND_API_KEY");
    return;
  }
  const plan = tier === "mechanic_pro" ? "Mechanic Pro" : "Premium";
  const amount = price?.unit_amount != null ? `$${(price.unit_amount / 100).toFixed(2)}` : "the plan price";
  const interval = price?.recurring?.interval === "year" ? "year" : "month";
  const p = `style="color: #444; font-size: 16px; line-height: 1.6; margin: 0 0 16px;"`;

  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${resendKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from: "My Car Companion <noreply@mycarcompanion.org>",
      to: [to],
      subject: `Your My Car Companion ${plan} subscription is active`,
      html: `
        <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
          <h2 style="color: #1B2B50; margin: 0 0 12px;">Thanks for subscribing to ${plan}</h2>
          <p ${p}>Here are the terms of your subscription:</p>
          <ul style="color: #444; font-size: 16px; line-height: 1.7;">
            <li><strong>Plan:</strong> ${plan}, billed every ${interval}</li>
            <li><strong>Price:</strong> ${amount} per ${interval}, plus any applicable tax</li>
            <li><strong>Renewal:</strong> Your subscription renews automatically at ${amount} every ${interval}
              until you cancel. ${interval === "year" ? "We will email you about 30 days before each annual renewal." : ""}</li>
            <li><strong>How to cancel:</strong> In the app, go to Settings, Manage Subscription. You can cancel
              anytime; you keep access until the end of the period you paid for and are not charged again.</li>
          </ul>
          <p ${p}>Stripe will also send you a separate payment receipt.</p>
          <p style="color: #888; font-size: 13px;">Questions? Email
            <a href="mailto:paul@thecooconsultant.com" style="color: #1B2B50;">paul@thecooconsultant.com</a>.</p>
        </div>`,
    }),
  });
  if (!res.ok) throw new Error(`Resend ${res.status}: ${(await res.text()).slice(0, 300)}`);
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

async function handlePaymentSucceeded(subscriptionId: string) {
  const subscription = await stripe.subscriptions.retrieve(subscriptionId);
  // Only restore access if the subscription is genuinely active
  if (subscription.status !== "active" && subscription.status !== "trialing") return;

  const customerId = subscription.customer as string;
  const priceId = subscription.items.data[0]?.price.id;
  const tier = tierFromPriceId(priceId);
  if (!tier) {
    console.warn("handlePaymentSucceeded: unknown priceId, skipping restore", priceId);
    return;
  }

  await supabase.from("profiles").update({
    is_premium: true,
    is_mechanic_pro: tier === "mechanic_pro",
    subscription_tier: tier,
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
