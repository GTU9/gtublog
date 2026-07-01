export const siteName = "GTU Automated Content Blog";
export const siteDescription =
  "A public blog for verified multi-topic posts backed by automated collection and editorial review.";
export const siteTagline = "Automated collection, careful review, and searchable public publishing";

export const siteUrl = (process.env.GTUBLOG_SITE_URL ?? "https://gtublog.dev").replace(/\/+$/, "");
export const publicApiBaseUrl = (
  process.env.GTUBLOG_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8080/api/v1/public"
).replace(/\/+$/, "");
export const applicationApiBaseUrl = (
  process.env.NEXT_PUBLIC_GTUBLOG_APPLICATION_API_BASE_URL
    ?? process.env.GTUBLOG_APPLICATION_API_BASE_URL
    ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/, "");

export const defaultRevalidateSeconds = 300;

export function absoluteUrl(pathname: string) {
  return `${siteUrl}${pathname.startsWith("/") ? pathname : `/${pathname}`}`;
}

export function isDevelopmentRuntime() {
  return process.env.NODE_ENV !== "production"
    && process.env.NEXT_PUBLIC_GTUBLOG_ENABLE_DEV_MOCKS !== "false";
}
