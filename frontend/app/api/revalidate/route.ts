import { revalidatePath, revalidateTag } from "next/cache";
import { revalidationBodyLimit, validRevalidationSecret, verifyRevalidationEvent } from "../../../src/revalidation";

export const runtime = "nodejs";

async function readBoundedBody(request: Request): Promise<Buffer | null> {
  const declaredLength = request.headers.get("content-length");
  if (declaredLength !== null && Number(declaredLength) > revalidationBodyLimit) return null;
  const reader = request.body?.getReader();
  if (!reader) return null;
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.length;
      if (length > revalidationBodyLimit) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  return Buffer.concat(chunks, length);
}

export async function POST(request: Request) {
  const secret = process.env.GTUBLOG_REVALIDATION_SHARED_SECRET;
  if (!validRevalidationSecret(secret)) return Response.json({ error: "Revalidation unavailable" }, { status: 503 });
  const body = await readBoundedBody(request);
  if (body === null) return Response.json({ error: "Invalid request" }, { status: 413 });
  const event = verifyRevalidationEvent(body, request.headers, secret);
  if (!event) return Response.json({ error: "Invalid request" }, { status: 401 });

  revalidateTag("public-posts", { expire: 0 });
  for (const path of new Set(event.paths)) revalidatePath(path);
  return Response.json({ revalidated: true, eventKey: event.eventKey });
}
