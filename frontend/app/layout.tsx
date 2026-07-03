import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

import { defaultMetadata } from "@/src/metadata";

export const metadata: Metadata = defaultMetadata();

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko" data-scroll-behavior="smooth">
      <body>
        <a href="#main-content" className="skip-link">
          본문으로 바로가기
        </a>
        {children}
      </body>
    </html>
  );
}
