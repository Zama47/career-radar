/**
 * Period presets shared by the analytics filters and the server pages. Periods are resolved
 * to concrete ISO dates so the API response stays stable for the duration of a render.
 */
export const periodOptions = [
  { value: "30", label: "30 дней", days: 30 },
  { value: "90", label: "90 дней", days: 90 },
  { value: "180", label: "6 месяцев", days: 180 },
  { value: "365", label: "Год", days: 365 },
  { value: "ALL", label: "Всё время", days: null as number | null },
];

export const DEFAULT_PERIOD = "90";

export type ResolvedPeriod = {
  from?: string;
  to?: string;
  label: string;
};

function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function resolvePeriod(period: string | undefined): ResolvedPeriod {
  const preset = periodOptions.find((option) => option.value === period) ?? periodOptions[1];
  if (preset.days == null) {
    return { label: "всё время" };
  }
  const to = new Date();
  const from = new Date(to.getTime() - (preset.days - 1) * 24 * 60 * 60 * 1000);
  return { from: isoDay(from), to: isoDay(to), label: preset.label.toLowerCase() };
}
