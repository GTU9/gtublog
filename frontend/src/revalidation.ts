import { createHmac, timingSafeEqual } from "node:crypto";

export const revalidationBodyLimit = 16 * 1024;

export type RevalidationEvent = {
  version: 1;
  eventKey: string;
  postId: number;
  slug: string;
  tags: string[];
  paths: string[];
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const slugPattern = /^[\p{L}\p{N}]+(?:-[\p{L}\p{N}]+)*$/u;
const fixedPaths = new Set(["/", "/archive", "/search", "/rss.xml", "/sitemap.xml"]);

export function validRevalidationSecret(secret: string | undefined): secret is string {
  return typeof secret === "string"
    && Buffer.byteLength(secret, "utf8") >= 32
    && !secret.toLowerCase().includes("placeholder")
    && !secret.toLowerCase().startsWith("replace-with-");
}

function validPublicPath(path: unknown): path is string {
  if (typeof path !== "string" || path.length > 256) return false;
  if (fixedPaths.has(path)) return true;
  const match = /^\/(posts|categories|tags)\/([^/?#]+)$/u.exec(path);
  return match !== null && match[2].length <= 160 && slugPattern.test(match[2]);
}

export function verifyRevalidationEvent(
  body: Buffer,
  headers: Headers,
  secret: string,
  nowSeconds = Math.floor(Date.now() / 1000),
): RevalidationEvent | null {
  if (body.length === 0 || body.length > revalidationBodyLimit || !validRevalidationSecret(secret)) return null;
  const timestamp = headers.get("X-Revalidation-Timestamp");
  const eventKey = headers.get("X-Revalidation-Event-Key");
  const signature = headers.get("X-Revalidation-Signature");
  if (!timestamp || !/^[0-9]{10,11}$/.test(timestamp) || !eventKey || !uuidPattern.test(eventKey)
    || !signature || !/^[0-9a-f]{64}$/.test(signature)) return null;
  const timestampSeconds = Number(timestamp);
  if (!Number.isSafeInteger(timestampSeconds) || timestampSeconds < nowSeconds - 300 || timestampSeconds > nowSeconds + 30) return null;

  const expected = createHmac("sha256", secret)
    .update(timestamp, "ascii").update("\n", "ascii")
    .update(eventKey, "ascii").update("\n", "ascii")
    .update(body).digest();
  if (!timingSafeEqual(expected, Buffer.from(signature, "hex"))) return null;

  let value: unknown;
  try { value = JSON.parse(body.toString("utf8")); }
  catch { return null; }
  if (value === null || typeof value !== "object" || Array.isArray(value)) return null;
  const event = value as Record<string, unknown>;
  if (event.version !== 1 || event.eventKey !== eventKey || !Number.isSafeInteger(event.postId)
    || (event.postId as number) <= 0 || typeof event.slug !== "string"
    || event.slug.length > 160 || !slugPattern.test(event.slug)) return null;
  if (!Array.isArray(event.tags) || event.tags.length !== 1 || event.tags[0] !== "public-posts") return null;
  if (!Array.isArray(event.paths) || event.paths.length < 1 || event.paths.length > 32
    || !event.paths.every(validPublicPath)) return null;
  return event as RevalidationEvent;
}
