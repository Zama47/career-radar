import { formatMoney } from "@/lib/format";
import type { SalaryStats } from "@/lib/types";

/**
 * Interquartile band: the p25–p75 range as a bar with a median marker. Deliberately not a
 * full box plot — the API returns percentiles only, so drawing whiskers would invent data.
 */
export function SalaryBand({ stats }: { stats: SalaryStats }) {
  const { p25, p75, median, currency } = stats;
  if (p25 == null || p75 == null || median == null) {
    return null;
  }

  const scaleMin = Math.max(0, Math.min(p25, median) * 0.6);
  const scaleMax = Math.max(p75, median) * 1.15;
  const span = scaleMax - scaleMin || 1;
  const position = (value: number) => ((value - scaleMin) / span) * 100;

  const markers = [
    { label: "p25", value: p25 },
    { label: "медиана", value: median },
    { label: "p75", value: p75 },
  ];

  return (
    <div className="space-y-6">
      <div className="relative mt-9 mb-10 h-3 rounded-full bg-muted">
        <div
          className="bg-primary/35 absolute inset-y-0 rounded-full"
          style={{ left: `${position(p25)}%`, width: `${position(p75) - position(p25)}%` }}
        />
        <div
          className="bg-primary absolute top-1/2 h-5 w-0.5 -translate-y-1/2 rounded"
          style={{ left: `${position(median)}%` }}
          aria-hidden
        />
        <div
          className="absolute -top-8 -translate-x-1/2 text-center text-[11px] leading-tight whitespace-nowrap"
          style={{ left: `${position(median)}%` }}
        >
          <span className="text-muted-foreground block">медиана</span>
          <span className="font-medium tabular-nums">{formatMoney(median, currency)}</span>
        </div>
        {[
          { label: "p25", value: p25 },
          { label: "p75", value: p75 },
        ].map(({ label, value }) => (
          <div
            key={label}
            className="absolute -bottom-8 -translate-x-1/2 text-center text-[11px] leading-tight whitespace-nowrap"
            style={{ left: `${position(value)}%` }}
          >
            <span className="text-muted-foreground block">{label}</span>
            <span className="font-medium tabular-nums">{formatMoney(value, currency)}</span>
          </div>
        ))}
      </div>

      <dl className="grid grid-cols-3 gap-3 text-sm">
        {markers.map(({ label, value }) => (
          <div key={label} className="rounded-lg border p-3">
            <dt className="text-muted-foreground text-xs capitalize">{label}</dt>
            <dd className="mt-1 font-medium tabular-nums">{formatMoney(value, currency)}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
