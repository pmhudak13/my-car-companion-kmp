// Public unsubscribe endpoint (deploy with verify_jwt false). GET from email links, POST for one-click
// List-Unsubscribe. Params: email, token (HMAC from _shared/email-compliance.ts), optional type.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { normalizeEmail, verifyUnsubscribeToken } from "../_shared/email-compliance.ts";

const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

// Preference columns an old per-type link may turn off. Anything else means "all".
const EMAIL_COLUMNS = [
  "email_trial_reminders", "email_renewal_reminders", "email_payment_receipts", "email_payment_failed",
  "email_subscription_cancelled", "email_new_messages", "email_mechanic_updates", "email_oil_change",
  "email_tire_rotation", "email_registration", "email_custom_reminders",
];

function page(title: string, message: string, status = 200): Response {
  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>${title}</title></head>
<body style="font-family: sans-serif; max-width: 560px; margin: 48px auto; padding: 0 16px; color: #1B2B50;">
<h1 style="font-size: 22px;">${title}</h1><p style="font-size: 16px; line-height: 1.6; color: #444;">${message}</p>
<p><a href="https://www.mycarcompanion.org/" style="color: #1B2B50;">My Car Companion</a></p></body></html>`;
  return new Response(html, { status, headers: { "Content-Type": "text/html; charset=utf-8" } });
}

Deno.serve(async (req) => {
  if (req.method !== "GET" && req.method !== "POST") return page("Not allowed", "Use the link in your email.", 405);

  const params = new URL(req.url).searchParams;
  const email = params.get("email") ?? "";
  const token = params.get("token") ?? "";
  const signed = token ? await verifyUnsubscribeToken(token) : null;
  if (!email || !signed || normalizeEmail(signed.email) !== normalizeEmail(email)) {
    return page("Link not valid", "This unsubscribe link is invalid or incomplete. Email paul@thecooconsultant.com and we will remove you.", 400);
  }

  const address = normalizeEmail(email);
  const type = signed.type; // trust the signed type, not the query string
  const columns = EMAIL_COLUMNS.includes(type) ? [type] : EMAIL_COLUMNS;

  // "All" also blocks emails to people without an account (invites, guest estimates, invoices).
  if (columns.length > 1) {
    const { error } = await supabase.from("email_suppressions").upsert({ email: address }, { onConflict: "email" });
    if (error) {
      console.error("Suppression insert failed:", error);
      return page("Something went wrong", "We could not process your request. Please try again or email paul@thecooconsultant.com.", 500);
    }
  }

  const { data: profile } = await supabase.from("profiles").select("user_id").ilike("email", address.replace(/[\\%_]/g, "\\$&")).limit(1).maybeSingle();
  if (profile?.user_id) {
    const off = Object.fromEntries(columns.map((c) => [c, false]));
    const { error } = await supabase.from("notification_preferences")
      .upsert({ user_id: profile.user_id, ...off }, { onConflict: "user_id" });
    if (error) console.error("Preference update failed:", error);
  }

  return page(
    "You are unsubscribed",
    columns.length > 1
      ? "You will no longer receive emails from My Car Companion at this address. Account security and legally required billing notices may still be sent."
      : "You will no longer receive this type of email from My Car Companion.",
  );
});
