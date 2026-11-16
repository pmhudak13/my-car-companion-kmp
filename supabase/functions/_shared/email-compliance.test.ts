import { assertEquals } from "jsr:@std/assert@1";
Deno.env.set("UNSUBSCRIBE_SECRET", "test-secret");
Deno.env.set("SUPABASE_URL", "https://example.supabase.co");
const m = await import("./email-compliance.ts");

Deno.test("token round-trips email and type", async () => {
  const t = await m.unsubscribeToken("a.b@x.com", "all");
  assertEquals(await m.verifyUnsubscribeToken(t), { email: "a.b@x.com", type: "all" });
});

Deno.test("tampered token is rejected", async () => {
  const t = await m.unsubscribeToken("a@x.com", "all");
  const forged = btoa("victim@x.com:all:1") + "." + t.split(".")[1];
  assertEquals(await m.verifyUnsubscribeToken(forged), null);
  assertEquals(await m.verifyUnsubscribeToken("garbage"), null);
});

Deno.test("footer requires a postal address", async () => {
  Deno.env.delete("COMPANY_POSTAL_ADDRESS");
  let threw = false;
  try { await m.complianceParts("a@x.com", "Shop"); } catch { threw = true; }
  assertEquals(threw, true);
  Deno.env.set("COMPANY_POSTAL_ADDRESS", "PO Box 1, LA CA <90001>");
  const { footer, headers } = await m.complianceParts("A@X.com", "Joe's <Shop>");
  assertEquals(footer.includes("PO Box 1, LA CA &lt;90001&gt;"), true);
  assertEquals(footer.includes("Joe's &lt;Shop&gt;"), true);
  assertEquals(headers["List-Unsubscribe"].includes("email=a%40x.com"), true);
});
