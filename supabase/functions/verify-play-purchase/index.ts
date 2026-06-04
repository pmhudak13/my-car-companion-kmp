import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * verify-play-purchase edge function
 *
 * Called from the Android app after a successful Google Play subscription purchase.
 * Verifies the purchase token with the Google Play Developer API, then updates the
 * user's subscription_tier and is_premium in the database.
 *
 * Required Supabase secrets:
 *   GOOGLE_SERVICE_ACCOUNT_JSON — JSON key for a service account with "Android Publisher" API access.
 *   See: https://developers.google.com/android-publisher/getting_started
 *
 * Product ID → subscription tier mapping:
 *   "premium"      → subscription_tier = "premium",      is_premium = true
 *   "mechanic_pro" → subscription_tier = "mechanic_pro", is_premium = true, is_mechanic_pro = true
 */

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

const TIER_MAP: Record<string, { subscription_tier: string; is_premium: boolean; is_mechanic_pro: boolean }> = {
  premium: { subscription_tier: "premium", is_premium: true, is_mechanic_pro: false },
  mechanic_pro: { subscription_tier: "mechanic_pro", is_premium: true, is_mechanic_pro: true },
};

/** Obtains a Google OAuth2 access token from a service account JSON key. */
async function getGoogleAccessToken(serviceAccountJson: string): Promise<string> {
  const sa = JSON.parse(serviceAccountJson);

  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

  const signingInput = `${encode(header)}.${encode(payload)}`;

  // Import the private key
  const keyData = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\n/g, "");
  const binaryKey = Uint8Array.from(atob(keyData), (c) => c.charCodeAt(0));
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryKey,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    new TextEncoder().encode(signingInput)
  );
  const signatureB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

  const jwt = `${signingInput}.${signatureB64}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const tokenData = await tokenRes.json();
  if (!tokenData.access_token) throw new Error("Failed to obtain Google access token");
  return tokenData.access_token;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  try {
    const authHeader = req.headers.get("Authorization");
    const token = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null;
    if (!token) return jsonResponse({ error: "Missing authorization" }, 401);

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    const { data: { user }, error: authError } = await supabase.auth.getUser(token);
    if (authError || !user) return jsonResponse({ error: "Unauthorized" }, 401);

    const { purchase_token, product_id, package_name } = await req.json();
    if (!purchase_token || !product_id || !package_name) {
      return jsonResponse({ error: "purchase_token, product_id, and package_name are required" }, 400);
    }

    const tier = TIER_MAP[product_id];
    if (!tier) return jsonResponse({ error: `Unknown product_id: ${product_id}` }, 400);

    // Verify with Google Play Developer API
    const serviceAccountJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
    if (!serviceAccountJson) {
      return jsonResponse({ error: "GOOGLE_SERVICE_ACCOUNT_JSON secret not configured" }, 500);
    }

    const accessToken = await getGoogleAccessToken(serviceAccountJson);

    const verifyUrl = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${package_name}/purchases/subscriptions/${product_id}/tokens/${purchase_token}`;
    const verifyRes = await fetch(verifyUrl, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    if (!verifyRes.ok) {
      const body = await verifyRes.text();
      console.error("Google Play API error:", verifyRes.status, body);
      return jsonResponse({ error: "Purchase could not be verified with Google Play" }, 402);
    }

    const purchaseData = await verifyRes.json();

    // paymentState: 0=pending, 1=received, 2=free trial, 3=deferred upgrade
    if (purchaseData.paymentState === 0) {
      return jsonResponse({ error: "Payment is still pending" }, 402);
    }

    // Update the user's subscription in the database
    const { error: updateError } = await supabase
      .from("profiles")
      .update({
        subscription_tier: tier.subscription_tier,
        is_premium: tier.is_premium,
        is_mechanic_pro: tier.is_mechanic_pro,
        play_purchase_token: purchase_token,
        play_product_id: product_id,
      })
      .eq("user_id", user.id);

    if (updateError) {
      console.error("DB update error:", updateError);
      return jsonResponse({ error: "Failed to update subscription" }, 500);
    }

    return jsonResponse({ success: true, tier: tier.subscription_tier });
  } catch (err) {
    console.error("verify-play-purchase error:", err);
    return jsonResponse({ error: "Internal server error" }, 500);
  }
});
