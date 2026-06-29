import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

import { siteDescription, siteName } from "@/src/site";

export const metadata: Metadata = {
  title: siteName,
  description: siteDescription,
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
