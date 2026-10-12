import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Emails a mechanic when an admin approves them. Called by the app right after approval;
// everything in the email is read server-side, so the caller only passes who was approved.

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const resendApiKey = Deno.env.get("RESEND_API_KEY") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-region",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function esc(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

function buildApprovedHtml(firstName: string | null, shopName: string): string {
  const p = `style="color: #444444; font-size: 16px; line-height: 1.6; margin: 0 0 16px;"`;
  return `
    <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #ffffff;">
      <div style="margin-bottom: 24px;">
        <img src="https://www.mycarcompanion.org/assets/logo.png" alt="My Car Companion" style="height: 32px;" />
      </div>
      <h2 style="color: #1B2B50; margin: 0 0 12px;">You're approved${firstName ? `, ${esc(firstName)}` : ""}!</h2>
      <p ${p}>
        Your mechanic profile for <strong>${esc(shopName)}</strong> has been verified. You now show up in the
        mechanic directory, and your full dashboard is unlocked.
      </p>
      <h3 style="color: #1B2B50; margin: 24px 0 8px;">Next step: bring in your past customers</h3>
      <p ${p}>
        Upload your old service records from a spreadsheet, then invite every customer in one tap.
        When they sign up with the email you have on file, their car's history from your shop is already there.
      </p>
      <p ${p}>
        <a href="https://www.mycarcompanion.org/import-records" style="color: #1B2B50; font-weight: bold;">
          See how to import your records &rarr;
        </a>
      </p>
      <div style="text-align: center; margin: 32px 0;">
        <a href="https://www.mycarcompanion.org/app/"
           style="background-color: #1B2B50; color: #ffffff; padding: 14px 28px;
                  text-decoration: none; border-radius: 8px; font-size: 15px; font-weight: bold;">
          Open Your Dashboard
        </a>
      </div>
      <hr style="border: none; border-top: 1px solid #eeeeee; margin: 32px 0;" />
      <p style="color: #999999; font-size: 12px; text-align: center; margin: 0;">
        You're receiving this because you applied to be a mechanic on My Car Companion.
      </p>
    </div>
  `;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse({ error: "Missing authorization header" }, 401);
  }
  const { data: { user }, error: authError } = await supabase.auth.getUser(authHeader.slice(7));
  if (authError || !user) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  const { data: adminRole } = await supabase
    .from("user_roles")
    .select("role")
    .eq("user_id", user.id)
    .eq("role", "admin")
    .maybeSingle();
  if (!adminRole) {
    return jsonResponse({ error: "Admin privileges required" }, 403);
  }

  let mechanicUserId: string | undefined;
  try {
    ({ mechanicUserId } = await req.json());
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }
  if (!mechanicUserId) {
    return jsonResponse({ error: "Missing mechanicUserId" }, 400);
  }

  // Only email someone who is actually verified, so a stray call can't send a false approval
  const { data: mechanic } = await supabase
    .from("mechanic_profiles")
    .select("shop_name, verification_status")
    .eq("user_id", mechanicUserId)
    .maybeSingle();
  if (!mechanic || mechanic.verification_status !== "verified") {
    return jsonResponse({ error: "Mechanic is not verified" }, 409);
  }

  const { data: profile } = await supabase
    .from("profiles")
    .select("email, first_name")
    .eq("user_id", mechanicUserId)
    .maybeSingle();
  if (!profile?.email) {
    return jsonResponse({ error: "Could not find the mechanic's email" }, 404);
  }

  const emailRes = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${resendApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: "My Car Companion <noreply@mycarcompanion.org>",
      to: [profile.email],
      subject: "You're approved on My Car Companion",
      html: buildApprovedHtml(profile.first_name, mechanic.shop_name ?? "your shop"),
    }),
  });

  if (!emailRes.ok) {
    const errText = await emailRes.text();
    console.error("Resend error:", errText);
    return jsonResponse({ error: `Email provider error (${emailRes.status})` }, 500);
  }

  return jsonResponse({ success: true }, 200);
});
