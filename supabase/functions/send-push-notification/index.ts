import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const FIREBASE_PROJECT_ID = "my-car-companion-16e0f";

// Maps notification type to its notification_preferences column
const PREF_COLUMN: Record<string, string> = {
  new_message: "new_messages",
  mechanic_update: "mechanic_updates",
  oil_change: "oil_change",
  tire_rotation: "tire_rotation",
  registration: "registration",
  custom: "custom_reminders",
};

interface PushPayload {
  recipient_id?: string;
  client_email?: string; // alternative lookup via profiles.email
  title: string;
  body: string;
  type?: string;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\n/g, "");
  const binary = atob(pemBody);
  const buffer = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) buffer[i] = binary.charCodeAt(i);
  return crypto.subtle.importKey(
    "pkcs8",
    buffer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64url(data: ArrayBuffer | string): string {
  const bytes =
    typeof data === "string"
      ? new TextEncoder().encode(data)
      : new Uint8Array(data);
  let str = "";
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function getAccessToken(serviceAccount: {
  client_email: string;
  private_key: string;
}): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64url(
    JSON.stringify({
      iss: serviceAccount.client_email,
      sub: serviceAccount.client_email,
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
    }),
  );

  const signingInput = `${header}.${payload}`;
  const key = await importPrivateKey(serviceAccount.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );

  const jwt = `${signingInput}.${base64url(signature)}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const tokenData = await tokenRes.json();
  if (!tokenData.access_token) {
    throw new Error(`Failed to get access token: ${JSON.stringify(tokenData)}`);
  }
  return tokenData.access_token;
}

Deno.serve(async (req) => {
  try {
    const payload: PushPayload = await req.json();
    const { title, body, type } = payload;
    let { recipient_id } = payload;

    if (!title || !body) {
      return new Response(JSON.stringify({ error: "Missing title or body" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

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
      return new Response(JSON.stringify({ skipped: "no recipient resolved" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Check notification preference for this type
    if (type) {
      const prefColumn = PREF_COLUMN[type] ?? null;
      if (prefColumn) {
        const { data: prefs } = await supabase
          .from("notification_preferences")
          .select(prefColumn)
          .eq("user_id", recipient_id)
          .maybeSingle();

        if (prefs && prefs[prefColumn] === false) {
          return new Response(JSON.stringify({ skipped: "user preference off" }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          });
        }
      }
    }

    // Look up FCM token
    const { data: tokenRow, error: tokenErr } = await supabase
      .from("device_tokens")
      .select("token")
      .eq("user_id", recipient_id)
      .eq("platform", "android")
      .maybeSingle();

    if (tokenErr || !tokenRow?.token) {
      return new Response(JSON.stringify({ skipped: "no device token" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    const serviceAccountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!serviceAccountJson) throw new Error("FIREBASE_SERVICE_ACCOUNT secret not set");
    const serviceAccount = JSON.parse(serviceAccountJson);
    const accessToken = await getAccessToken(serviceAccount);

    const fcmRes = await fetch(
      `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          message: {
            token: tokenRow.token,
            notification: { title, body },
            data: { type: type ?? "" },
            android: {
              notification: {
                channel_id: "mycarcompanion_default",
                sound: "default",
              },
            },
          },
        }),
      },
    );

    const fcmData = await fcmRes.json();
    if (!fcmRes.ok) throw new Error(`FCM error: ${JSON.stringify(fcmData)}`);

    return new Response(JSON.stringify({ success: true, name: fcmData.name }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error(err);
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
