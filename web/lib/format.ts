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

export function formatMoney(value: number | null | undefined, currency?: string | null): string {
  if (value == null) return "нет данных";
  return `${numberFormat.format(value)} ${currencySymbol(currency)}`.trim();
}

const compactNumber = new Intl.NumberFormat("ru-RU", {
  notation: "compact",
  maximumFractionDigits: 1,
});

/** Compact count for chart axes: 1200 -> "1,2 тыс.". */
export function formatCompact(value: number): string {
  return compactNumber.format(value);
}

/** Compact salary for chart axes: 80000 -> "80 тыс.". */
export function formatAxisMoney(value: number): string {
  if (Math.abs(value) < 1000) return String(value);
  return `${numberFormat.format(Math.round(value / 1000))} тыс.`;
}

const shortDateFormat = new Intl.DateTimeFormat("ru-RU", {
  day: "numeric",
  month: "short",
  timeZone: "UTC",
});

/** Formats an ISO bucket date (YYYY-MM-DD) as "1 окт." without timezone drift. */
export function formatShortDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? iso : shortDateFormat.format(date);
}

/** Russian plural form for 1 / 2–4 / 5+. */
export function pluralizeRu(count: number, one: string, few: string, many: string): string {
  const mod10 = Math.abs(count) % 10;
  const mod100 = Math.abs(count) % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

export function vacancyCount(count: number): string {
  return `${count} ${pluralizeRu(count, "вакансия", "вакансии", "вакансий")}`;
}

/** Accusative form for "на N вакансий больше". */
export function vacancyCountAccusative(count: number): string {
  return `${count} ${pluralizeRu(count, "вакансию", "вакансии", "вакансий")}`;
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
