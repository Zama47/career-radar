import Link from "next/link";
import {
  ArrowRight,
  BarChart3,
  BriefcaseBusiness,
  Building2,
  CalendarPlus,
  Laptop,
  PieChart,
} from "lucide-react";

import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { VacancyTable } from "@/components/vacancy-table";
import { ApiError, getDashboard, getRecentVacancies } from "@/lib/api";
import { formatPercent } from "@/lib/format";
import type { Dashboard, VacancyPage } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  let dashboard: Dashboard;
  let recent: VacancyPage;
  try {
    [dashboard, recent] = await Promise.all([getDashboard(), getRecentVacancies(5)]);
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

  const kpis = [
    {
      label: "Всего вакансий",
      value: dashboard.totalVacancies.toLocaleString("ru-RU"),
      icon: BriefcaseBusiness,
      hint: "активные вакансии в базе",
    },
    {
      label: "Новых сегодня",
      value: dashboard.newToday.toLocaleString("ru-RU"),
      icon: CalendarPlus,
      hint: "впервые найдены за сегодня (UTC)",
    },
    {
      label: "Стажировок",
      value: dashboard.internships.toLocaleString("ru-RU"),
      icon: Building2,
      hint: "грейд INTERN",
    },
    {
      label: "Удалённо",
      value: formatPercent(dashboard.remoteShare),
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
        <Button asChild>
          <Link href="/vacancies">
            Все вакансии
            <ArrowRight className="size-4" />
          </Link>
        </Button>
      </header>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="KPI">
        {kpis.map(({ label, value, icon: Icon, hint }) => (
          <Card key={label}>
            <CardHeader className="pb-2">
              <CardDescription className="flex items-center gap-2">
                <Icon className="size-4" />
                {label}
              </CardDescription>
              <CardTitle className="text-3xl tabular-nums">{value}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-xs">{hint}</p>
            </CardContent>
          </Card>
        ))}
      </section>

      <section className="grid gap-4 lg:grid-cols-2" aria-label="Графики (заглушки)">
        {[
          {
            title: "Профессии и навыки",
            description: "Топ профессий и требуемых технологий",
            icon: BarChart3,
          },
          {
            title: "Зарплаты и формат работы",
            description: "Распределение зарплат и remote share",
            icon: PieChart,
          },
        ].map(({ title, description, icon: Icon }) => (
          <Card key={title}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Icon className="size-4" />
                {title}
              </CardTitle>
              <CardDescription>{description}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="border-muted text-muted-foreground flex h-40 items-center justify-center rounded-lg border border-dashed text-sm">
                Графики появятся в stage 4
              </div>
            </CardContent>
          </Card>
        ))}
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
