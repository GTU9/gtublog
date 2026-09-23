export function pageIndex(value: string | string[] | undefined): number {
  if (typeof value !== "string" || !/^[1-9]\d*$/.test(value)) return 0;
  const page = Number(value);
  return Number.isSafeInteger(page) && page <= 1_000_000 ? page - 1 : 0;
}

export function pageHref(basePath: string, index: number): string {
  const [pathname, query = ""] = basePath.split("?");
  const params = new URLSearchParams(query);
  if (index === 0) params.delete("page");
  else params.set("page", String(index + 1));
  return `${pathname}${params.size ? `?${params.toString()}` : ""}`;
}
