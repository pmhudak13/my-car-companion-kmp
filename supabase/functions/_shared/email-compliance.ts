// CAN-SPAM helpers for emails sent to people who may not have an account:
// suppression check, signed unsubscribe link, and a footer with sender + postal address.
import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";

export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function esc(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export async function isSuppressed(supabase: SupabaseClient, email: string): Promise<boolean> {
  const { data, error } = await supabase
    .from("email_suppressions")
    .select("email")
    .eq("email", normalizeEmail(email))
    .maybeSingle();
  // Fail closed: if we can't check the list, don't risk mailing someone who opted out.
  if (error) throw new Error(`Suppression check failed: ${error.message}`);
  return !!data;
}

async function hmacKey(usage: "sign" | "verify"): Promise<CryptoKey> {
  const secret = Deno.env.get("UNSUBSCRIBE_SECRET");
  if (!secret) throw new Error("UNSUBSCRIBE_SECRET not configured");
  return crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [usage]);
}

// Same token scheme as generateUnsubscribeToken in check-renewal-reminders: base64(email:type:ts).base64(hmac)
export async function unsubscribeToken(email: string, type: string): Promise<string> {
  const data = `${email}:${type}:${Date.now()}`;
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", await hmacKey("sign"), new TextEncoder().encode(data)));
  return `${btoa(data)}.${btoa(String.fromCharCode(...sig))}`;
}

// Returns the email and type the token was signed for, or null if the signature is bad.
export async function verifyUnsubscribeToken(token: string): Promise<{ email: string; type: string } | null> {
  const [dataB64, sigB64] = token.split(".");
  if (!dataB64 || !sigB64) return null;
  try {
    const data = atob(dataB64);
    const sig = Uint8Array.from(atob(sigB64), (c) => c.charCodeAt(0));
    const ok = await crypto.subtle.verify("HMAC", await hmacKey("verify"), sig, new TextEncoder().encode(data));
    if (!ok) return null;
    const lastColon = data.lastIndexOf(":");
    const rest = data.slice(0, lastColon); // email:type
    const typeColon = rest.lastIndexOf(":");
    return { email: rest.slice(0, typeColon), type: rest.slice(typeColon + 1) };
  } catch {
    return null;
  }
}

export async function unsubscribeUrl(email: string, type = "all"): Promise<string> {
  const token = await unsubscribeToken(email, type);
  return `${SUPABASE_URL}/functions/v1/email-unsubscribe?email=${encodeURIComponent(email)}&type=${type}&token=${encodeURIComponent(token)}`;
}

export function postalAddress(): string {
  const address = Deno.env.get("COMPANY_POSTAL_ADDRESS")?.trim();
  if (!address) throw new Error("COMPANY_POSTAL_ADDRESS secret not set; refusing to send without a mailing address");
  return address;
}

// Footer and headers for one recipient. Throws if the postal address or unsubscribe secret is missing.
export async function complianceParts(email: string, sender: string) {
  const address = postalAddress();
  const url = await unsubscribeUrl(normalizeEmail(email));
  const footer = `
    <hr style="border: none; border-top: 1px solid #eeeeee; margin: 32px 0 16px;" />
    <p style="color: #666666; font-size: 12px; line-height: 1.6; text-align: center; margin: 0;">
      Sent by ${esc(sender)} via My Car Companion.<br />
      My Car Companion, ${esc(address)}<br />
      <a href="${url}" style="color: #1B2B50;">Unsubscribe</a> from emails sent through My Car Companion.
    </p>`;
  const headers = {
    "List-Unsubscribe": `<${url}>`,
    "List-Unsubscribe-Post": "List-Unsubscribe=One-Click",
  };
  return { footer, headers };
}
