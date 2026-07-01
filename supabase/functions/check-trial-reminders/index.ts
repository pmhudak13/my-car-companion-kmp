import { serve } from "https://deno.land/std@0.190.0/http/server.ts";
import Stripe from "https://esm.sh/stripe@18.5.0";
import { Resend } from "https://esm.sh/resend@2.0.0";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.57.2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-cron-secret",
};

const logStep = (step: string, details?: any) => {
  const detailsStr = details ? ` - ${JSON.stringify(details)}` : '';
  console.log(`[CHECK-TRIAL-REMINDERS] ${step}${detailsStr}`);
};

const SUPABASE_PROJECT_URL = Deno.env.get("SUPABASE_URL") || "";
const APP_URL = Deno.env.get("APP_URL") || "";

// Generate secure HMAC-based unsubscribe token
async function generateUnsubscribeToken(email: string, type: string): Promise<string> {
  const secret = Deno.env.get('UNSUBSCRIBE_SECRET');
  if (!secret) {
    throw new Error('UNSUBSCRIBE_SECRET not configured');
  }

  const timestamp = Date.now();
  const data = `${email}:${type}:${timestamp}`;

  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign(
    'HMAC',
    key,
    new TextEncoder().encode(data)
  );

  // Convert signature to base64
  const sigArray = new Uint8Array(signature);
  let binary = '';
  for (let i = 0; i < sigArray.length; i++) {
    binary += String.fromCharCode(sigArray[i]);
  }
  const sigB64 = btoa(binary);

  // Token format: base64(data).base64(signature)
  return `${btoa(data)}.${sigB64}`;
}

async function generateUnsubscribeUrl(email: string, type: string): Promise<string> {
  const token = await generateUnsubscribeToken(email, type);
  return `${SUPABASE_PROJECT_URL}/functions/v1/email-unsubscribe?email=${encodeURIComponent(email)}&type=${type}&token=${encodeURIComponent(token)}`;
}

async function generateEmailFooter(email: string, emailType: string): Promise<string> {
  const unsubscribeUrl = await generateUnsubscribeUrl(email, emailType);
  const unsubscribeAllUrl = await generateUnsubscribeUrl(email, 'all');

  return `
    <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;">

    <p style="color: #9ca3af; font-size: 12px; text-align: center;">
      You're receiving this email because you signed up for a trial at AutoLog.<br>
      <a href="${unsubscribeUrl}" style="color: #9ca3af;">Unsubscribe from these emails</a>
      &nbsp;|&nbsp;
      <a href="${unsubscribeAllUrl}" style="color: #9ca3af;">Unsubscribe from all</a>
      &nbsp;|&nbsp;
      <a href="${APP_URL}/notifications" style="color: #9ca3af;">Manage preferences</a>
    </p>
  `;
}

async function checkEmailPreference(
  supabaseClient: any,
  email: string,
  preferenceKey: string
): Promise<boolean> {
  try {
    // Find user by email
    const { data: profiles, error: profileError } = await supabaseClient
      .from("profiles")
      .select("user_id")
      .eq("email", email)
      .limit(1);

    if (profileError || !profiles || profiles.length === 0) {
      logStep("No profile found for email, defaulting to allow email", { email });
      return true; // Default to sending if no profile found
    }

    const userId = profiles[0].user_id;

    // Check notification preferences
    const { data: prefs, error: prefsError } = await supabaseClient
      .from("notification_preferences")
      .select(preferenceKey)
      .eq("user_id", userId)
      .limit(1);

    if (prefsError || !prefs || prefs.length === 0) {
      logStep("No preferences found, defaulting to allow email", { userId });
      return true; // Default to sending if no preferences set
    }

    const isEnabled = prefs[0][preferenceKey] !== false; // Default true if null
    logStep("Email preference checked", { email, preferenceKey, isEnabled });
    return isEnabled;
  } catch (error) {
    logStep("Error checking email preference, defaulting to allow", { error: String(error) });
    return true;
  }
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  // Cron-only: require the shared secret so this can't be triggered publicly.
  const cronSecret = Deno.env.get("CRON_SECRET");
  if (!cronSecret || req.headers.get("x-cron-secret") !== cronSecret) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 401,
    });
  }

  try {
    logStep("Function started");

    const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
    if (!stripeKey) throw new Error("STRIPE_SECRET_KEY is not set");

    const resendKey = Deno.env.get("RESEND_API_KEY");
    if (!resendKey) throw new Error("RESEND_API_KEY is not set");

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !supabaseServiceKey) throw new Error("Supabase credentials not set");

    const stripe = new Stripe(stripeKey, { apiVersion: "2025-08-27.basil" });
    const resend = new Resend(resendKey);
    const supabaseClient = createClient(supabaseUrl, supabaseServiceKey, {
      auth: { persistSession: false }
    });

    // Calculate the date range for trials ending in 1-2 days
    const now = Math.floor(Date.now() / 1000);
    const oneDayFromNow = now + 86400; // 24 hours
    const twoDaysFromNow = now + 172800; // 48 hours

    logStep("Searching for trials ending soon", {
      from: new Date(oneDayFromNow * 1000).toISOString(),
      to: new Date(twoDaysFromNow * 1000).toISOString()
    });

    // Get subscriptions that are in trial and ending soon
    const subscriptions = await stripe.subscriptions.list({
      status: "trialing",
      limit: 100,
    });

    logStep("Found trialing subscriptions", { count: subscriptions.data.length });

    let emailsSent = 0;
    let emailsSkipped = 0;
    const errors: string[] = [];

    for (const subscription of subscriptions.data) {
      if (!subscription.trial_end) continue;

      // Check if trial ends within 1-2 days
      if (subscription.trial_end >= oneDayFromNow && subscription.trial_end <= twoDaysFromNow) {
        try {
          // Get customer email
          const customer = await stripe.customers.retrieve(subscription.customer as string);
          if (customer.deleted || !('email' in customer) || !customer.email) {
            logStep("Customer has no email", { customerId: subscription.customer });
            continue;
          }

          // Check user's email preference
          const canSendEmail = await checkEmailPreference(
            supabaseClient,
            customer.email,
            "email_trial_reminders"
          );

          if (!canSendEmail) {
            logStep("User opted out of trial reminder emails", { email: customer.email });
            emailsSkipped++;
            continue;
          }

          const trialEndDate = new Date(subscription.trial_end * 1000);
          const daysRemaining = Math.ceil((subscription.trial_end - now) / 86400);

          logStep("Sending trial reminder email", {
            email: customer.email,
            trialEnd: trialEndDate.toISOString(),
            daysRemaining
          });

          // Generate secure email footer
          const emailFooter = await generateEmailFooter(customer.email, 'email_trial_reminders');

          // Send the reminder email
          const emailResult = await resend.emails.send({
            from: "My Car Companion <hello@mycarcompanion.org>",
            to: [customer.email],
            subject: `Your free trial ends in ${daysRemaining} day${daysRemaining > 1 ? 's' : ''}`,
            html: `
              <!DOCTYPE html>
              <html>
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
              </head>
              <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="text-align: center; margin-bottom: 30px;">
                  <h1 style="color: #2563eb; margin: 0;">AutoLog</h1>
                </div>

                <div style="background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%); border-radius: 12px; padding: 24px; margin-bottom: 24px;">
                  <h2 style="margin: 0 0 12px 0; color: #92400e;">⏰ Your Trial Ends Soon!</h2>
                  <p style="margin: 0; color: #78350f;">
                    Your free trial will end on <strong>${trialEndDate.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</strong>.
                  </p>
                </div>

                <p>Hi there,</p>

                <p>We hope you've been enjoying AutoLog! Your 7-day free trial is coming to an end ${daysRemaining === 1 ? 'tomorrow' : `in ${daysRemaining} days`}.</p>

                <p>To continue enjoying all the premium features without interruption:</p>

                <ul style="padding-left: 20px;">
                  <li>Unlimited vehicles</li>
                  <li>Custom reminder intervals</li>
                  <li>Mileage + time hybrid reminders</li>
                  <li>Priority support</li>
                  <li>And much more!</li>
                </ul>

                <p>If you don't take any action, your subscription will automatically continue and your card will be charged.</p>

                <p>If you'd like to cancel, you can do so anytime from your account settings before the trial ends.</p>

                <div style="text-align: center; margin: 32px 0;">
                  <a href="${APP_URL}/plans"
                     style="display: inline-block; background: #2563eb; color: white; padding: 14px 28px; border-radius: 8px; text-decoration: none; font-weight: 600;">
                    Manage Subscription
                  </a>
                </div>

                <p style="color: #6b7280; font-size: 14px;">
                  Thanks for being part of AutoLog!<br>
                  — The AutoLog Team
                </p>

                ${emailFooter}
              </body>
              </html>
            `,
          });

          logStep("Email sent successfully", { email: customer.email, result: emailResult });
          emailsSent++;
        } catch (emailError) {
          const errorMsg = emailError instanceof Error ? emailError.message : String(emailError);
          logStep("Error sending email", { error: errorMsg });
          errors.push(errorMsg);
        }
      }
    }

    logStep("Completed", { emailsSent, emailsSkipped, errors: errors.length });

    return new Response(JSON.stringify({
      success: true,
      emailsSent,
      emailsSkipped,
      errors: errors.length > 0 ? errors : undefined
    }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 200,
    });
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    logStep("ERROR", { message: errorMessage });
    return new Response(JSON.stringify({ error: errorMessage }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 500,
    });
  }
});
