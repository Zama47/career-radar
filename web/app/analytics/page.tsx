import Link from "next/link";
import { ArrowLeft } from "lucide-react";

import { AnalyticsFilters } from "@/components/analytics-filters";
import { ChartFrame } from "@/components/charts/chart-frame";
import { HorizontalBarChart } from "@/components/charts/horizontal-bar-chart";
import { RemoteShare } from "@/components/charts/remote-share";
import { SalaryBand } from "@/components/charts/salary-band";
import { TimelineChart } from "@/components/charts/timeline-chart";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import {
  ApiError,
  getAnalyticsOverview,
  getAnalyticsSalaries,
  getAnalyticsSkills,
  getAnalyticsTimeline,
  getProfessions,
} from "@/lib/api";
import { chartColors } from "@/lib/chart-theme";
import {
  formatMoney,
  formatPercent,
  pluralizeRu,
  vacancyCount,
  vacancyCountAccusative,
} from "@/lib/format";
import { DEFAULT_PERIOD, resolvePeriod } from "@/lib/period";
import type {
  AnalyticsOverview,
  AnalyticsSkills,
  AnalyticsTimeline,
  Profession,
  SalaryStats,
  TimelinePoint,
} from "@/lib/types";

export const dynamic = "force-dynamic";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

function first(value: string | string[] | undefined): string {
  if (Array.isArray(value)) {
    return value[0] ?? "";
  }
  return value ?? "";
}

function timelineInsight(points: TimelinePoint[]): string | undefined {
  if (points.length === 0) return undefined;
  if (points.length === 1) {
    return `В одну неделю попали ${vacancyCount(points[0].count)} — сравнивать пока не с чем.`;
  }
  const previous = points[points.length - 2];
  const last = points[points.length - 1];
  const delta = last.count - previous.count;
  if (delta === 0) {
    return `Последние две недели стабильны: по ${vacancyCount(last.count)}.`;
  }
  const percent =
    previous.count >= 5 ? Math.round((Math.abs(delta) / previous.count) * 100) : null;
  const suffix = percent == null ? "" : ` (${delta > 0 ? "+" : "−"}${percent}%)`;
  return delta > 0
    ? `Публикаций больше на ${vacancyCountAccusative(delta)}${suffix}. Спрос растёт — имеет смысл пересмотреть приоритеты откликов.`
    : `Публикаций меньше на ${vacancyCountAccusative(Math.abs(delta))}${suffix}. Возможно, сказывается сезон или выбранный фильтр.`;
}

function salaryInsight(stats: SalaryStats): string | undefined {
  if (stats.sampleSize === 0 || stats.median == null) return undefined;
  const range =
    stats.p25 != null && stats.p75 != null
      ? ` Половина вакансий платит от ${formatMoney(stats.p25, stats.currency)} до ${formatMoney(stats.p75, stats.currency)}.`
      : "";
  return `Выборка: ${vacancyCount(stats.sampleSize)} с указанной зарплатой. Медиана — ${formatMoney(stats.median, stats.currency)}.${range}`;
}

function skillsInsight(skills: AnalyticsSkills): string | undefined {
  if (skills.items.length === 0) return undefined;
  const top = skills.items.slice(0, 3);
  return `Чаще всего требуют ${top
    .map((item) => `${item.skill} (${formatPercent(item.share)})`)
    .join(", ")}. Это минимальный стек для отклика.`;
}

function professionsInsight(overview: AnalyticsOverview): string | undefined {
  if (overview.topProfessions.length === 0) return undefined;
  const [top, second] = overview.topProfessions;
  return `${top.name} — самая ёмкая профессия (${vacancyCount(top.count)}).${
    second ? ` Дальше идёт ${second.name} — ${vacancyCount(second.count)}.` : ""
  }`;
}

function remoteInsight(overview: AnalyticsOverview): string | undefined {
  if (overview.totalVacancies === 0) return undefined;
  return overview.remoteShare >= 0.5
    ? `Больше половины вакансий выборки — полностью удалённые. География не ограничивает.`
    : `Удалёнка — миноритарный формат: ${formatPercent(overview.remoteShare)}. Офис и гибрид всё ещё в приоритете у работодателей.`;
}

export default async function AnalyticsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const filters = {
    profession: first(params.profession),
    seniority: first(params.seniority),
    remote: first(params.remote),
    period: first(params.period) || DEFAULT_PERIOD,
  };
  const range = resolvePeriod(filters.period);
  const query = {
    from: range.from,
    to: range.to,
    profession: filters.profession,
    seniority: filters.seniority,
    remote: filters.remote,
  };

  let overview: AnalyticsOverview;
  let timeline: AnalyticsTimeline;
  let skills: AnalyticsSkills;
  let salaries: SalaryStats;
  let professions: Profession[];
  try {
    [overview, timeline, skills, salaries, professions] = await Promise.all([
      getAnalyticsOverview({ ...query, top: 10 }),
      getAnalyticsTimeline({ ...query, bucket: "week" }),
      getAnalyticsSkills({ ...query, limit: 20 }),
      getAnalyticsSalaries(query),
      getProfessions(),
    ]);
  } catch (error) {
    if (error instanceof ApiError) {
      return (
        <main className="mx-auto w-full max-w-6xl px-4 py-8">
          <ErrorState message={error.message} retryHref="/analytics" />
        </main>
      );
    }
    throw error;
  }

  return (
    <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Аналитика рынка</h1>
          <p className="text-muted-foreground text-sm">
            Динамика, зарплаты, навыки и формат работы. Данные — карьерные сайты компаний,
            период: {range.label}.
          </p>
        </div>
        <Button variant="outline" asChild>
          <Link href="/">
            <ArrowLeft className="size-4" />
            На дашборд
          </Link>
        </Button>
      </header>

      <AnalyticsFilters
        key={`${filters.profession}|${filters.seniority}|${filters.remote}|${filters.period}`}
        professions={professions}
        current={filters}
        basePath="/analytics"
      />

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Сводка">
        {[
          { label: "Вакансий", value: overview.totalVacancies.toLocaleString("ru-RU") },
          { label: "Новых", value: overview.newVacancies.toLocaleString("ru-RU") },
          {
            label: "Медиана",
            value: formatMoney(overview.medianSalary, overview.currency),
          },
          { label: "Удалённо", value: formatPercent(overview.remoteShare) },
        ].map((item) => (
          <div key={item.label} className="bg-card rounded-lg border p-4">
            <p className="text-muted-foreground text-xs">{item.label}</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums">{item.value}</p>
          </div>
        ))}
      </section>

      <section className="grid gap-4 xl:grid-cols-2" aria-label="Графики аналитики">
        <ChartFrame
          title="Динамика по неделям"
          description="Количество опубликованных вакансий"
          badge={range.label}
          empty={timeline.points.length === 0}
          insight={timelineInsight(timeline.points)}
          className="xl:col-span-2"
        >
          <TimelineChart points={timeline.points} />
        </ChartFrame>

        <ChartFrame
          title="Распределение зарплат"
          description="Межквартильный диапазон p25–p75 и медиана"
          badge={`${salaries.sampleSize} с зарплатой`}
          empty={salaries.sampleSize === 0 || salaries.median == null}
          insight={salaryInsight(salaries)}
        >
          <SalaryBand stats={salaries} />
        </ChartFrame>

        <ChartFrame
          title="Remote share"
          description="Доля вакансий, которые можно закрыть удалённо"
          badge={range.label}
          empty={overview.totalVacancies === 0}
          insight={remoteInsight(overview)}
        >
          <RemoteShare share={overview.remoteShare} total={overview.totalVacancies} />
        </ChartFrame>

        <ChartFrame
          title="Топ навыков"
          description="20 самых частых требований в вакансиях выборки"
          badge={`${skills.items.length} ${pluralizeRu(skills.items.length, "навык", "навыка", "навыков")}`}
          empty={skills.items.length === 0}
          insight={skillsInsight(skills)}
        >
          <HorizontalBarChart
            data={skills.items.map((item) => ({ label: item.skill, value: item.count }))}
            color={chartColors.teal}
            height={Math.max(200, skills.items.length * 30 + 32)}
          />
        </ChartFrame>

        <ChartFrame
          title="Сравнение профессий"
          description="Сколько активных вакансий у каждой профессии"
          badge={range.label}
          empty={overview.topProfessions.length === 0}
          insight={professionsInsight(overview)}
        >
          <HorizontalBarChart
            data={overview.topProfessions.map((item) => ({
              label: item.name,
              value: item.count,
            }))}
            color={chartColors.violet}
          />
        </ChartFrame>
      </section>
    </main>
  );
}
