import Link from "next/link";
import { ArrowLeft, ArrowRight } from "lucide-react";

import { EmptyState, ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { VacancyFilters } from "@/components/vacancy-filters";
import { VacancyTable } from "@/components/vacancy-table";
import { ApiError, getProfessions, getVacancies } from "@/lib/api";
import { buildQuery } from "@/lib/query";
import type { Profession, VacancyPage } from "@/lib/types";

export const dynamic = "force-dynamic";

const PAGE_SIZE = 20;

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

function first(value: string | string[] | undefined): string {
  if (Array.isArray(value)) {
    return value[0] ?? "";
  }
  return value ?? "";
}

export default async function VacanciesPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  const params = await searchParams;
  const filters = {
    q: first(params.q),
    profession: first(params.profession),
    seniority: first(params.seniority),
    remote: first(params.remote),
    salaryMin: first(params.salaryMin),
    sort: first(params.sort) || "publishedAt,desc",
  };
  const requestedPage = Number.parseInt(first(params.page), 10);
  const pageIndex = Number.isFinite(requestedPage) && requestedPage > 0 ? requestedPage : 0;

  let data: VacancyPage;
  let professions: Profession[];
  try {
    [data, professions] = await Promise.all([
      getVacancies({ ...filters, page: pageIndex, size: PAGE_SIZE }),
      getProfessions(),
    ]);
  } catch (error) {
    if (error instanceof ApiError) {
      return (
        <main className="mx-auto w-full max-w-6xl px-4 py-8">
          <ErrorState message={error.message} retryHref="/vacancies" />
        </main>
      );
    }
    throw error;
  }

  const totalPages = Math.max(1, Math.ceil(data.total / data.size));
  const professionNames = Object.fromEntries(
    professions.map((profession) => [profession.code, profession.name]),
  );
  const hasFilters = Boolean(
    filters.q ||
      filters.profession ||
      filters.seniority ||
      filters.remote ||
      filters.salaryMin,
  );
  const pageHref = (target: number) => `/vacancies${buildQuery({ ...filters, page: target })}`;

  return (
    <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Вакансии</h1>
        <p className="text-muted-foreground text-sm">
          Найдено{" "}
          <span className="text-foreground font-medium tabular-nums">
            {data.total.toLocaleString("ru-RU")}
          </span>{" "}
          вакансий. Фильтры сохраняются в URL — ссылкой можно поделиться.
        </p>
      </header>

      <VacancyFilters
        key={`${filters.q}|${filters.profession}|${filters.seniority}|${filters.remote}|${filters.salaryMin}|${filters.sort}|${data.page}`}
        professions={professions}
        current={filters}
      />

      {data.items.length === 0 ? (
        <EmptyState
          title="Ничего не найдено"
          message={
            hasFilters
              ? "Под выбранные фильтры вакансий нет. Попробуйте ослабить условия."
              : "База пуста. Запустите коллектор, чтобы собрать вакансии с карьерных сайтов."
          }
          actionHref={hasFilters ? "/vacancies" : undefined}
          actionLabel={hasFilters ? "Сбросить фильтры" : undefined}
        />
      ) : (
        <Card className="py-2">
          <CardContent className="px-2">
            <VacancyTable items={data.items} professionNames={professionNames} />
          </CardContent>
        </Card>
      )}

      {totalPages > 1 && (
        <nav className="flex flex-wrap items-center justify-between gap-3" aria-label="Пагинация">
          <p className="text-muted-foreground text-sm">
            Страница {data.page + 1} из {totalPages}
          </p>
          <div className="flex gap-2">
            {data.page > 0 ? (
              <Button variant="outline" size="sm" asChild>
                <Link href={pageHref(data.page - 1)}>
                  <ArrowLeft className="size-4" />
                  Назад
                </Link>
              </Button>
            ) : (
              <Button variant="outline" size="sm" disabled>
                <ArrowLeft className="size-4" />
                Назад
              </Button>
            )}
            {data.page + 1 < totalPages ? (
              <Button variant="outline" size="sm" asChild>
                <Link href={pageHref(data.page + 1)}>
                  Вперёд
                  <ArrowRight className="size-4" />
                </Link>
              </Button>
            ) : (
              <Button variant="outline" size="sm" disabled>
                Вперёд
                <ArrowRight className="size-4" />
              </Button>
            )}
          </div>
        </nav>
      )}
    </main>
  );
}
