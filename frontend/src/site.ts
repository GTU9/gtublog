export const siteName = "지튜 블로그";
export const siteDescription =
  "자동 수집과 직접 검토를 바탕으로 다양한 주제를 정리해 발행하는 한국어 정보 블로그입니다.";
export const siteTagline = "자동화로 모으고, 사람의 판단으로 정리하는 기록형 블로그";

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
