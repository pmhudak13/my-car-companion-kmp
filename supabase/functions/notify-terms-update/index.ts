import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// One-off blast: notify all users that the Terms of Service and Privacy Policy
// were updated on July 6, 2026. Gated by CRON_SECRET so it can't be triggered
// publicly. Invoke with {"dryRun": true} first to see the recipient count.

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-cron-secret",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

const EMAIL_SUBJECT = "We've updated our Terms of Service and Privacy Policy";

const EMAIL_HTML = `
  <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
    <h2 style="color: #1a1a1a;">We've updated our Terms &amp; Privacy Policy</h2>
    <p style="color: #444; font-size: 16px; line-height: 1.5;">
      Hi there — we've made some updates to the My Car Companion
      <a href="https://www.mycarcompanion.org/terms.html" style="color: #4F46E5;">Terms of Service</a>
      and
      <a href="https://www.mycarcompanion.org/privacy.html" style="color: #4F46E5;">Privacy Policy</a>,
      effective <strong>July 6, 2026</strong>. Here's a summary of what changed:
    </p>
    <ul style="color: #444; font-size: 15px; line-height: 1.7;">
      <li><strong>AI features:</strong> We explain how the AI invoice-scanning feature works,
        that scanned documents are processed by our AI provider (Anthropic), and that you
        should review AI-extracted records before saving them.</li>
      <li><strong>Dispute resolution:</strong> We added a standard arbitration agreement with a
        class action waiver. <strong>You can opt out</strong> by emailing
        <a href="mailto:paul@thecooconsultant.com?subject=Arbitration%20Opt-Out" style="color: #4F46E5;">paul@thecooconsultant.com</a>
        with the subject "Arbitration Opt-Out" within 30 days of this notice.</li>
      <li><strong>Copyright (DMCA):</strong> We added a process for reporting content that
        infringes copyright.</li>
      <li><strong>User content:</strong> We clarified responsibility for content that users
        submit, and added a standard indemnification section.</li>
    </ul>
    <p style="color: #444; font-size: 16px; line-height: 1.5;">
      No action is needed — continuing to use My Car Companion after July 6, 2026 means you
      accept the updated terms. If you have questions, just reply to this email.
    </p>
    <p style="color: #888; font-size: 13px; margin-top: 32px;">
      My Car Companion · <a href="https://www.mycarcompanion.org" style="color: #888;">mycarcompanion.org</a><br>
      You received this because you have a My Car Companion account.
    </p>
  </div>
`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  const cronSecret = Deno.env.get("CRON_SECRET");
  if (!cronSecret || req.headers.get("x-cron-secret") !== cronSecret) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  let dryRun = true;
  try {
    const body = await req.json();
    dryRun = body?.dryRun !== false; // must explicitly pass {"dryRun": false} to send
  } catch {
    // no body → dry run
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Collect all confirmed user emails
  const emails = new Set<string>();
  let page = 1;
  while (true) {
    const { data, error } = await supabase.auth.admin.listUsers({
      page,
      perPage: 1000,
    });
    if (error) {
      return jsonResponse({ error: `listUsers failed: ${error.message}` }, 500);
    }
    for (const user of data.users) {
      if (user.email && user.email_confirmed_at) {
        emails.add(user.email.toLowerCase());
      }
    }
    if (data.users.length < 1000) break;
    page++;
  }

  const recipients = [...emails];

  if (dryRun) {
    return jsonResponse(
      {
        dryRun: true,
        recipientCount: recipients.length,
        sample: recipients.slice(0, 5),
        subject: EMAIL_SUBJECT,
      },
      200,
    );
  }

  // Send via Resend batch API (max 100 per request)
  const resendApiKey = Deno.env.get("RESEND_API_KEY")!;
  let sent = 0;
  const failures: string[] = [];

  for (let i = 0; i < recipients.length; i += 100) {
    const chunk = recipients.slice(i, i + 100);
    const batch = chunk.map((to) => ({
      from: "My Car Companion <noreply@mycarcompanion.org>",
      to: [to],
      subject: EMAIL_SUBJECT,
      html: EMAIL_HTML,
    }));

    const res = await fetch("https://api.resend.com/emails/batch", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(batch),
    });

    if (res.ok) {
      sent += chunk.length;
    } else {
      const errText = await res.text();
      console.error(`Resend batch failed (offset ${i}):`, errText);
      failures.push(`offset ${i}: ${errText.slice(0, 200)}`);
    }
  }

  return jsonResponse(
    { dryRun: false, recipientCount: recipients.length, sent, failures },
    200,
  );
});
