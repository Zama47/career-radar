import { formatPercent } from "@/lib/format";

export function RemoteShare({ share, total }: { share: number; total: number }) {
  const percent = Math.min(100, Math.max(0, share * 100));

  return (
    <div className="space-y-4">
      <div className="flex items-baseline gap-2">
        <span className="text-4xl font-semibold tabular-nums">{formatPercent(share)}</span>
        <span className="text-muted-foreground text-sm">вакансий можно делать удалённо</span>
      </div>
      <div
        className="bg-muted h-2.5 w-full overflow-hidden rounded-full"
        role="img"
        aria-label={`Remote share ${formatPercent(share)}`}
      >
        <div className="h-full rounded-full bg-teal-600" style={{ width: `${percent}%` }} />
      </div>
      <p className="text-muted-foreground text-xs">
        Из {total.toLocaleString("ru-RU")} активных вакансий за выбранный период.
      </p>
    </div>
  );
}
