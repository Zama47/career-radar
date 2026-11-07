import type { RemoteType, Seniority, SalaryPeriod } from "@/lib/types";

export const seniorityLabels: Record<Seniority, string> = {
  INTERN: "Стажёр",
  JUNIOR: "Junior",
  MIDDLE: "Middle",
  SENIOR: "Senior",
  LEAD: "Lead",
  UNKNOWN: "Не указан",
};

export const remoteLabels: Record<RemoteType, string> = {
  OFFICE: "Офис",
  HYBRID: "Гибрид",
  REMOTE: "Удалённо",
  UNKNOWN: "Не указан",
};

const salaryPeriodSuffix: Record<SalaryPeriod, string> = {
  MONTH: "",
  YEAR: " / год",
  DAY: " / день",
  HOUR: " / час",
  UNKNOWN: "",
};

const numberFormat = new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 0 });
const dateFormat = new Intl.DateTimeFormat("ru-RU", {
  day: "2-digit",
  month: "short",
  year: "numeric",
});

function currencySymbol(currency: string | null | undefined): string {
  if (!currency) return "";
  if (currency.toUpperCase() === "RUB") return "₽";
  return currency.toUpperCase();
}

export function formatSalary(
  from: number | null | undefined,
  to: number | null | undefined,
  currency: string | null | undefined,
  period: SalaryPeriod | undefined,
): string {
  const symbol = currencySymbol(currency);
  const suffix = salaryPeriodSuffix[period ?? "MONTH"];
  if (from != null && to != null && from !== to) {
    return `${numberFormat.format(from)} – ${numberFormat.format(to)} ${symbol}${suffix}`.trim();
  }
  if (from != null) {
    return `от ${numberFormat.format(from)} ${symbol}${suffix}`.trim();
  }
  if (to != null) {
    return `до ${numberFormat.format(to)} ${symbol}${suffix}`.trim();
  }
  return "Зарплата не указана";
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "—" : dateFormat.format(date);
}

export function formatPercent(share: number): string {
  return `${Math.round(share * 100)}%`;
}

/** Turns dictionary codes such as JAVA_BACKEND into a readable fallback label. */
export function humanizeCode(code: string | null | undefined): string {
  if (!code) return "—";
  return code
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
