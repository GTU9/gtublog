import type { Metadata } from "next";

import { absoluteUrl, siteDescription, siteName } from "@/src/site";

export function buildMetadata({
  title,
  description,
  pathname,
}: {
  title: string;
  description: string;
  pathname: string;
}): Metadata {
  const canonical = absoluteUrl(pathname);

  return {
    title,
    description,
    alternates: {
      canonical,
    },
    openGraph: {
      title,
      description,
      url: canonical,
      siteName,
      type: pathname.startsWith("/posts/") ? "article" : "website",
      locale: "ko_KR",
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
    },
  };
}

export function defaultMetadata(): Metadata {
  return buildMetadata({
    title: siteName,
    description: siteDescription,
    pathname: "/",
  });
}

