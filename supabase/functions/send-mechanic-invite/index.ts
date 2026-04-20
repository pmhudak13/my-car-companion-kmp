import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const resendApiKey = Deno.env.get("RESEND_API_KEY") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  // Verify JWT
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "Missing authorization header" }), {
      status: 401,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  const token = authHeader.replace("Bearer ", "");
  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  let body: {
    jobId: string;
    clientEmail: string;
    clientName: string;
    mechanicName: string;
    vehicleInfo: string;
  };

  try {
    body = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
      status: 400,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  const { jobId, clientEmail, clientName, mechanicName, vehicleInfo } = body;

  if (!jobId || !clientEmail || !clientName || !mechanicName || !vehicleInfo) {
    return new Response(JSON.stringify({ error: "Missing required fields" }), {
      status: 400,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  // Verify the job belongs to the requesting mechanic (service role bypasses RLS so we check manually)
  const { data: job, error: jobError } = await supabase
    .from("mechanic_jobs")
    .select("id, mechanic_user_id")
    .eq("id", jobId)
    .eq("mechanic_user_id", user.id)
    .single();

  if (jobError || !job) {
    return new Response(JSON.stringify({ error: "Job not found or access denied" }), {
      status: 403,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  // Send invite email via Resend
  const emailRes = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${resendApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: "My Car Companion <noreply@mycarcompanion.app>",
      to: [clientEmail],
      subject: `${mechanicName} invited you to track your ${vehicleInfo}`,
      html: `
        <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
          <h2 style="color: #1a1a1a;">Hi ${clientName},</h2>
          <p style="color: #444; font-size: 16px; line-height: 1.5;">
            <strong>${mechanicName}</strong> has invited you to join <strong>My Car Companion</strong>
            to track service history for your <strong>${vehicleInfo}</strong>.
          </p>
          <p style="color: #444; font-size: 16px; line-height: 1.5;">
            With My Car Companion you can view your vehicle's full service history, get maintenance reminders,
            and communicate with your mechanic directly through the app.
          </p>
          <div style="text-align: center; margin: 32px 0;">
            <a href="https://mycarcompanion.app"
               style="background-color: #4F46E5; color: white; padding: 14px 28px;
                      text-decoration: none; border-radius: 8px; font-size: 16px; font-weight: bold;">
              Download My Car Companion
            </a>
          </div>
          <p style="color: #888; font-size: 13px; margin-top: 32px;">
            If you weren't expecting this email, you can safely ignore it.
          </p>
        </div>
      `,
    }),
  });

  if (!emailRes.ok) {
    const errText = await emailRes.text();
    console.error("Resend error:", errText);
    return new Response(JSON.stringify({ error: "Failed to send email", detail: errText }), {
      status: 500,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  // Mark invite as sent on the job
  const { error: updateError } = await supabase
    .from("mechanic_jobs")
    .update({ invite_sent: true })
    .eq("id", jobId);

  if (updateError) {
    console.error("Failed to update invite_sent:", updateError);
  }

  return new Response(JSON.stringify({ success: true }), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    },
  });
});
