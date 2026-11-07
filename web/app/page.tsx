import Link from "next/link";
import { ArrowRight, BriefcaseBusiness, CalendarPlus, Laptop, Wallet } from "lucide-react";

import { AnalyticsFilters } from "@/components/analytics-filters";
import { ChartFrame } from "@/components/charts/chart-frame";
import { HorizontalBarChart } from "@/components/charts/horizontal-bar-chart";
import { TimelineChart } from "@/components/charts/timeline-chart";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { VacancyTable } from "@/components/vacancy-table";
import {
  ApiError,
  getAnalyticsOverview,
  getAnalyticsSkills,
  getAnalyticsTimeline,
  getProfessions,
  getRecentVacancies,
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
  TimelinePoint,
  VacancyPage,
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
    return `В выборке ${vacancyCount(points[0].count)}. Для динамики нужно хотя бы два периода.`;
  }
  const previous = points[points.length - 2];
  const last = points[points.length - 1];
  const delta = last.count - previous.count;
  if (delta === 0) {
    return `Число вакансий не изменилось: ${vacancyCount(last.count)} в двух последних неделях.`;
  }
  const percent =
    previous.count >= 5 ? Math.round((Math.abs(delta) / previous.count) * 100) : null;
  const suffix = percent == null ? "" : ` (${delta > 0 ? "+" : "−"}${percent}%)`;
  return delta > 0
    ? `За последнюю неделю на ${vacancyCountAccusative(delta)} больше${suffix}. Рынок оживает — следите за свежими публикациями.`
    : `За последнюю неделю на ${vacancyCountAccusative(Math.abs(delta))} меньше${suffix}. Стоит быстрее откликаться на новые вакансии.`;
}

function skillsInsight(skills: AnalyticsSkills): string | undefined {
  if (skills.items.length === 0) return undefined;
  const top = skills.items.slice(0, 3);
  return `Чаще всего требуют: ${top
    .map((item) => `${item.skill} (${formatPercent(item.share)})`)
    .join(", ")}. Это минимальный набор для отклика.`;
}

function professionsInsight(overview: AnalyticsOverview): string | undefined {
  const [top, second] = overview.topProfessions;
  if (!top) return undefined;
  return `${top.name} — ${vacancyCount(top.count)}${
    second ? `; следующая профессия — ${second.name} (${vacancyCount(second.count)})` : ""
  }.`;
}

export default async function DashboardPage({ searchParams }: { searchParams: SearchParams }) {
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
  let professions: Profession[];
  let recent: VacancyPage;
  try {
    [overview, timeline, skills, professions, recent] = await Promise.all([
      getAnalyticsOverview({ ...query, top: 6 }),
      getAnalyticsTimeline({ ...query, bucket: "week" }),
      getAnalyticsSkills({ ...query, limit: 8 }),
      getProfessions(),
      getRecentVacancies(5),
    ]);
  } catch (error) {
    if (error instanceof ApiError) {
      return (
        <main className="mx-auto w-full max-w-6xl px-4 py-8">
          <ErrorState message={error.message} retryHref="/" />
        </main>
      );
    }
    throw error;
  }

  const newHint =
    filters.period === "ALL"
      ? "впервые найдены за сегодня (UTC)"
      : `впервые найдены за ${range.label}`;

  const kpis = [
    {
      label: "Всего вакансий",
      value: overview.totalVacancies.toLocaleString("ru-RU"),
      icon: BriefcaseBusiness,
      hint: `активные вакансии: ${range.label}`,
    },
    {
      label: "Новых",
      value: overview.newVacancies.toLocaleString("ru-RU"),
      icon: CalendarPlus,
      hint: newHint,
    },
    {
      label: "Медианная зарплата",
      value: formatMoney(overview.medianSalary, overview.currency),
      icon: Wallet,
      hint:
        overview.p25Salary != null && overview.p75Salary != null
          ? `p25 ${formatMoney(overview.p25Salary, overview.currency)} · p75 ${formatMoney(overview.p75Salary, overview.currency)}`
          : "нет вакансий с указанной зарплатой",
    },
    {
      label: "Удалённо",
      value: formatPercent(overview.remoteShare),
      icon: Laptop,
      hint: "доля REMOTE-вакансий",
    },
  ];

  return (
    <main className="mx-auto w-full max-w-6xl space-y-8 px-4 py-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground text-sm">
            Рынок стажировок и junior-вакансий Москвы по данным карьерных сайтов компаний.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" asChild>
            <Link href="/analytics">Аналитика</Link>
          </Button>
          <Button asChild>
            <Link href="/vacancies">
              Все вакансии
              <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
      </header>

      <AnalyticsFilters
        key={`${filters.profession}|${filters.seniority}|${filters.remote}|${filters.period}`}
        professions={professions}
        current={filters}
        basePath="/"
      />

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="KPI">
        {kpis.map(({ label, value, icon: Icon, hint }) => (
          <Card key={label}>
            <CardHeader className="pb-2">
              <CardDescription className="flex items-center gap-2">
                <Icon className="size-4" />
                {label}
              </CardDescription>
              <CardTitle className="text-2xl tabular-nums">{value}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-xs">{hint}</p>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="grid gap-4 xl:grid-cols-2" aria-label="Графики рынка">
        <ChartFrame
          title="Вакансии во времени"
          description="Сколько вакансий опубликовано в каждую неделю"
          badge={range.label}
          empty={timeline.points.length === 0}
          insight={timelineInsight(timeline.points)}
          className="xl:col-span-2"
        >
          <TimelineChart points={timeline.points} />
        </ChartFrame>

        <ChartFrame
          title="Топ навыков"
          description="Как часто навык встречается в вакансиях выборки"
          badge={`${skills.items.length} ${pluralizeRu(skills.items.length, "навык", "навыка", "навыков")}`}
          empty={skills.items.length === 0}
          insight={skillsInsight(skills)}
        >
          <HorizontalBarChart
            data={skills.items.map((item) => ({ label: item.skill, value: item.count }))}
            color={chartColors.teal}
          />
        </ChartFrame>

        <ChartFrame
          title="Топ профессий"
          description="Активные вакансии по профессиям"
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

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Последние вакансии</h2>
          <Link
            href="/vacancies"
            className="text-muted-foreground hover:text-foreground text-sm underline-offset-4 hover:underline"
          >
            Смотреть все
          </Link>
        </div>
        <Card className="py-2">
          <CardContent className="px-2">
            {recent.items.length === 0 ? (
              <p className="text-muted-foreground py-8 text-center text-sm">
                Вакансий пока нет. Запустите коллектор, чтобы наполнить базу.
              </p>
            ) : (
              <VacancyTable items={recent.items} professionNames={{}} />
            )}
          </CardContent>
        </Card>
      </section>
    </main>
  );
}
