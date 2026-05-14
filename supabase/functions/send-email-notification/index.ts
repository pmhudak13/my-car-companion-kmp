import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// Maps notification type to its email preference column
const EMAIL_PREF_COLUMN: Record<string, string> = {
  new_message: "email_new_messages",
  mechanic_update: "email_mechanic_updates",
  oil_change: "email_oil_change",
  tire_rotation: "email_tire_rotation",
  registration: "email_registration",
  custom: "email_custom_reminders",
};

interface EmailPayload {
  recipient_id?: string;
  client_email?: string; // alternative lookup via profiles.email
  title: string;        // becomes the email subject
  body: string;         // becomes the plain-text body content
  type?: string;        // used for preference check
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

function buildEmailHtml(title: string, body: string): string {
  return `
    <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #ffffff;">
      <div style="margin-bottom: 24px;">
        <img
          src="https://www.mycarcompanion.org/logo.png"
          alt="My Car Companion"
          style="height: 32px;"
          onerror="this.style.display='none'"
        />
      </div>
      <h2 style="color: #1B2B50; margin: 0 0 12px;">${escapeHtml(title)}</h2>
      <p style="color: #444444; font-size: 16px; line-height: 1.6; margin: 0 0 24px;">
        ${escapeHtml(body)}
      </p>
      <div style="text-align: center; margin: 32px 0;">
        <a
          href="https://www.mycarcompanion.org/app/"
          style="background-color: #1B2B50; color: #ffffff; padding: 14px 28px;
                 text-decoration: none; border-radius: 8px; font-size: 15px; font-weight: bold;"
        >
          Open My Car Companion
        </a>
      </div>
      <hr style="border: none; border-top: 1px solid #eeeeee; margin: 32px 0;" />
      <p style="color: #999999; font-size: 12px; text-align: center; margin: 0;">
        You're receiving this because you have email notifications enabled in My Car Companion.
        <br />
        <a href="https://www.mycarcompanion.org/app/" style="color: #6BBCDE;">Manage preferences</a>
      </p>
    </div>
  `;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse({ error: "Missing authorization" }, 401);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const token = authHeader.slice(7);
  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  let payload: EmailPayload;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const { title, body, type } = payload;
  let { recipient_id } = payload;

  if (!title || !body) {
    return jsonResponse({ error: "Missing title or body" }, 400);
  }

  // Resolve recipient via client_email if recipient_id not supplied
  if (!recipient_id && payload.client_email) {
    const { data: profileRow } = await supabase
      .from("profiles")
      .select("user_id")
      .eq("email", payload.client_email)
      .maybeSingle();
    recipient_id = profileRow?.user_id ?? undefined;
  }

  if (!recipient_id) {
    return jsonResponse({ skipped: "no recipient resolved" }, 200);
  }

  // Check email preference for this notification type
  if (type) {
    const prefColumn = EMAIL_PREF_COLUMN[type] ?? null;
    if (prefColumn) {
      const { data: prefs } = await supabase
        .from("notification_preferences")
        .select(prefColumn)
        .eq("user_id", recipient_id)
        .maybeSingle();

      if (prefs && prefs[prefColumn] === false) {
        return jsonResponse({ skipped: "user email preference off" }, 200);
      }
    }
  }

  // Look up the recipient's email address
  const { data: profile, error: profileError } = await supabase
    .from("profiles")
    .select("email")
    .eq("user_id", recipient_id)
    .maybeSingle();

  if (profileError || !profile?.email) {
    return jsonResponse({ skipped: "no email address on file" }, 200);
  }

  const resendApiKey = Deno.env.get("RESEND_API_KEY");
  if (!resendApiKey) throw new Error("RESEND_API_KEY secret not set");

  const emailRes = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${resendApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: "My Car Companion <noreply@mycarcompanion.org>",
      to: [profile.email],
      subject: title,
      html: buildEmailHtml(title, body),
    }),
  });

  if (!emailRes.ok) {
    const errText = await emailRes.text();
    console.error("Resend error:", errText);
    return jsonResponse({ error: "Failed to send email" }, 500);
  }

  return jsonResponse({ success: true }, 200);
});
