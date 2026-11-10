import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// GET ?t=<invoice_token>: invoice data for the public page at /invoice/ (no account needed; the
//   token is an unguessable per-job UUID, like a "view invoice" link from any invoicing tool).
// POST { job_id } with the mechanic's JWT: emails that link to the job's customer.

const SITE = "https://www.mycarcompanion.org";
const ALLOWED_ORIGINS = [SITE, "https://mycarcompanion.org"];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

function cors(req: Request) {
  const origin = req.headers.get("Origin") ?? "";
  return {
    "Access-Control-Allow-Origin": ALLOWED_ORIGINS.includes(origin) ? origin : SITE,
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-region",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Vary": "Origin",
  };
}

function json(req: Request, body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store", ...cors(req) },
  });
}

function esc(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

const JOB_COLUMNS = "id, mechanic_user_id, invoice_number, invoice_token, created_at, completed_at, status, " +
  "client_name, client_email, vehicle_year, vehicle_make, vehicle_model, vehicle_vin, vehicle_license_plate, " +
  "vehicle_color, mileage_in, description, tax_rate, total_cost, payment_received, estimate_signed_name, estimate_approved_at";

async function shopFor(mechanicUserId: string) {
  const [{ data: shop }, { data: profile }] = await Promise.all([
    supabase.from("mechanic_profiles").select(
      "shop_name, phone, address, city, state, zip_code, logo_path, pay_zelle, pay_venmo, pay_cashapp, " +
        "pay_paypal, pay_card_link, payment_notes, invoice_terms",
    ).eq("user_id", mechanicUserId).maybeSingle(),
    supabase.from("profiles").select("email").eq("user_id", mechanicUserId).maybeSingle(),
  ]);
  let logo_url: string | null = null;
  if (shop?.logo_path) {
    const { data } = await supabase.storage.from("shop-logos").createSignedUrl(shop.logo_path, 60 * 60);
    logo_url = data?.signedUrl ?? null;
  }
  const { logo_path: _omit, ...rest } = shop ?? {};
  return { ...rest, email: profile?.email ?? null, logo_url };
}

async function handleGet(req: Request): Promise<Response> {
  const token = new URL(req.url).searchParams.get("t") ?? "";
  if (!UUID.test(token)) return json(req, { error: "Invoice not found" }, 404);

  const { data: job } = await supabase.from("mechanic_jobs").select(JOB_COLUMNS).eq("invoice_token", token).maybeSingle();
  if (!job) return json(req, { error: "Invoice not found" }, 404);

  const [{ data: lines }, shop] = await Promise.all([
    supabase.from("mechanic_job_line_items").select("kind, description, quantity, unit_price")
      .eq("mechanic_job_id", job.id).order("created_at"),
    shopFor(job.mechanic_user_id),
  ]);
  const { id: _id, mechanic_user_id: _m, invoice_token: _t, ...publicJob } = job;
  return json(req, { job: publicJob, lines: lines ?? [], shop });
}

async function handlePost(req: Request): Promise<Response> {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return json(req, { error: "Missing authorization" }, 401);
  const { data: { user } } = await supabase.auth.getUser(auth.slice(7));
  if (!user) return json(req, { error: "Unauthorized" }, 401);

  let body: { job_id?: string };
  try {
    body = await req.json();
  } catch {
    return json(req, { error: "Invalid JSON body" }, 400);
  }
  if (!body.job_id || !UUID.test(body.job_id)) return json(req, { error: "Missing job_id" }, 400);

  const { data: job } = await supabase.from("mechanic_jobs").select(JOB_COLUMNS)
    .eq("id", body.job_id).eq("mechanic_user_id", user.id).maybeSingle();
  if (!job) return json(req, { error: "Job not found" }, 404);
  const to = job.client_email?.trim();
  if (!to) return json(req, { error: "Add the customer's email to the job first" }, 400);

  const shop = await shopFor(user.id);
  const shopName = shop.shop_name || "Your mechanic";
  const link = `${SITE}/invoice/?t=${job.invoice_token}`;
  const total = job.total_cost != null ? `$${Number(job.total_cost).toFixed(2)}` : null;
  const vehicle = `${job.vehicle_year} ${job.vehicle_make} ${job.vehicle_model}`;

  const resendKey = Deno.env.get("RESEND_API_KEY");
  if (!resendKey) return json(req, { error: "Email is not set up" }, 500);
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${resendKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from: `${shopName.replace(/[<>"]/g, "")} via My Car Companion <noreply@mycarcompanion.org>`,
      to: [to],
      ...(shop.email ? { reply_to: shop.email } : {}),
      subject: `Invoice #${job.invoice_number} from ${shopName}`,
      html: `
        <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
          <h2 style="color: #1B2B50; margin: 0 0 12px;">Invoice #${job.invoice_number}</h2>
          <p style="color: #444; font-size: 16px; line-height: 1.6;">
            Hi ${esc(job.client_name || "there")}, here is your invoice from <strong>${esc(shopName)}</strong>
            for your ${esc(vehicle)}${total ? `. Balance due: <strong>${total}</strong>` : ""}.
          </p>
          <div style="text-align: center; margin: 32px 0;">
            <a href="${link}" style="background-color: #1B2B50; color: #fff; padding: 14px 28px; text-decoration: none;
               border-radius: 8px; font-size: 15px; font-weight: bold;">View and pay invoice</a>
          </div>
          <p style="color: #999; font-size: 12px;">Questions? Reply to this email to reach ${esc(shopName)}.</p>
        </div>`,
    }),
  });
  if (!res.ok) {
    console.error("Resend error:", await res.text());
    return json(req, { error: "Failed to send email" }, 500);
  }
  return json(req, { success: true });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors(req) });
  try {
    if (req.method === "GET") return await handleGet(req);
    if (req.method === "POST") return await handlePost(req);
    return json(req, { error: "Method not allowed" }, 405);
  } catch (e) {
    console.error(e);
    return json(req, { error: "Something went wrong" }, 500);
  }
});
