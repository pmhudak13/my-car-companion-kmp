import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const corsHeaders = {
  "Access-Control-Allow-Origin": "https://www.mycarcompanion.org",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-region",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return new Response(JSON.stringify({ error: "Missing authorization" }), {
      status: 401,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }

  // Verify the calling user's JWT and get their ID
  const userClient = createClient(supabaseUrl, supabaseServiceKey, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: { user }, error: authError } = await userClient.auth.getUser();
  if (authError || !user) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }

  const adminClient = createClient(supabaseUrl, supabaseServiceKey);

  // Files first: once the user row is gone nothing points at them. Any failure stops here so the
  // user can retry, rather than ending up with no account and their photos still stored.
  const { data: files, error: filesError } = await adminClient.rpc("storage_files_for_user", { p_user_id: user.id });
  if (filesError) {
    console.error("delete-account storage listing error:", filesError);
    return new Response(JSON.stringify({ error: "Failed to delete account" }), {
      status: 500,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }
  const pathsByBucket = new Map<string, string[]>();
  for (const f of (files ?? []) as { bucket_id: string; name: string }[]) {
    pathsByBucket.set(f.bucket_id, [...(pathsByBucket.get(f.bucket_id) ?? []), f.name]);
  }
  for (const [bucket, paths] of pathsByBucket) {
    // Storage removes at most 1000 paths per call; 100 keeps each request small
    for (let i = 0; i < paths.length; i += 100) {
      const { error: removeError } = await adminClient.storage.from(bucket).remove(paths.slice(i, i + 100));
      if (removeError) {
        console.error(`delete-account storage remove error (${bucket}):`, removeError);
        return new Response(JSON.stringify({ error: "Failed to delete account" }), {
          status: 500,
          headers: { "Content-Type": "application/json", ...corsHeaders },
        });
      }
    }
  }

  // Service-role delete of the user cascades to all user data via FK
  const { error: deleteError } = await adminClient.auth.admin.deleteUser(user.id);
  if (deleteError) {
    console.error("delete-account error:", deleteError);
    return new Response(JSON.stringify({ error: "Failed to delete account" }), {
      status: 500,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }

  return new Response(JSON.stringify({ success: true }), {
    status: 200,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
});
