// Deploy with Supabase Edge Functions. Never ship the service-role key to the Android app.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const url = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anon || !service) return new Response("Not configured", { status: 503 });
  const bearer = request.headers.get("Authorization") ?? "";
  if (!bearer.startsWith("Bearer ")) return new Response("Unauthorized", { status: 401 });
  const client = createClient(url, anon, { global: { headers: { Authorization: bearer } } });
  const { data: { user }, error: authError } = await client.auth.getUser();
  if (authError || !user) return new Response("Unauthorized", { status: 401 });
  const admin = createClient(url, service);
  // Delete owned storage objects before deleting the auth user.
  // This deployment assumes receipt photos are stored under the user-id prefix.
  const bucket = "receipt-photos";
  const { data: objects, error: listError } = await admin.storage.from(bucket).list(user.id, { limit: 1000 });
  if (listError && !/not found/i.test(listError.message)) return new Response("Unable to remove stored photos", { status: 500 });
  if (objects?.length) {
    const paths = objects.filter((item) => item.name && !item.id?.startsWith("folder")).map((item) => `${user.id}/${item.name}`);
    if (paths.length) {
      const { error } = await admin.storage.from(bucket).remove(paths);
      if (error) return new Response("Unable to remove stored photos", { status: 500 });
    }
  }
  // Foreign keys with ON DELETE CASCADE remove user-owned rows.
  const { error } = await admin.auth.admin.deleteUser(user.id);
  if (error) return new Response("Account deletion failed", { status: 500 });
  return new Response(JSON.stringify({ deleted: true }), { status: 200, headers: { "Content-Type": "application/json" } });
});
