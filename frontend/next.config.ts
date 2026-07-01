import type { NextConfig } from "next";

const scriptPolicy = process.env.NODE_ENV === "development"
  ? "script-src 'self' 'unsafe-inline' 'unsafe-eval'"
  : "script-src 'self' 'unsafe-inline'";

const applicationApiBaseUrl = process.env.GTUBLOG_APPLICATION_API_BASE_URL ?? "http://127.0.0.1:8080/api/v1";
const applicationApiOrigin = applicationApiBaseUrl.startsWith("/") ? null : new URL(applicationApiBaseUrl).origin;
const connectPolicy = `connect-src 'self'${applicationApiOrigin ? ` ${applicationApiOrigin}` : ""}`;

const nextConfig: NextConfig = {
  allowedDevOrigins: ["127.0.0.1"],
  output: "standalone",
  poweredByHeader: false,
  async headers() {
    return [{
      source: "/(.*)",
      headers: [
        { key: "Content-Security-Policy", value: `default-src 'self'; ${scriptPolicy}; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; ${connectPolicy}; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "X-Frame-Options", value: "DENY" },
        { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
      ],
    }];
  },
};

export default nextConfig;
