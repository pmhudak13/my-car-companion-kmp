import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const resendApiKey = Deno.env.get("RESEND_API_KEY") ?? "";

const supabase = createClient(supabaseUrl, supabaseServiceKey);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  // Verify JWT
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return jsonResponse({ error: "Missing authorization header" }, 401);
  }

  const token = authHeader.replace("Bearer ", "");
  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) {
    return jsonResponse({ error: "Unauthorized" }, 401);
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
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const { jobId, clientEmail, clientName, mechanicName, vehicleInfo } = body;

  if (!jobId || !clientEmail || !clientName || !mechanicName || !vehicleInfo) {
    return jsonResponse({ error: "Missing required fields" }, 400);
  }

  // Verify the job belongs to the requesting mechanic
  const { data: job, error: jobError } = await supabase
    .from("mechanic_jobs")
    .select("id, mechanic_user_id")
    .eq("id", jobId)
    .eq("mechanic_user_id", user.id)
    .single();

  if (jobError || !job) {
    return jsonResponse({ error: "Job not found or access denied" }, 403);
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
    return jsonResponse({ error: "Failed to send email" }, 500);
  }

  // Mark invite as sent on the job
  const { error: updateError } = await supabase
    .from("mechanic_jobs")
    .update({ invite_sent: true })
    .eq("id", jobId);

  if (updateError) {
    console.error("Failed to update invite_sent:", updateError);
  }

  return jsonResponse({ success: true }, 200);
});
