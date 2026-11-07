import Link from "next/link";
import { notFound } from "next/navigation";
import {
  ArrowLeft,
  Building2,
  CalendarDays,
  ExternalLink,
  MapPin,
  Wallet,
} from "lucide-react";

import { ErrorState } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError, getVacancy } from "@/lib/api";
import { formatDate, formatSalary, humanizeCode, remoteLabels, seniorityLabels } from "@/lib/format";
import type { VacancyDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

type Params = Promise<{ id: string }>;

export default async function VacancyDetailPage({ params }: { params: Params }) {
  const { id } = await params;

  let vacancy: VacancyDetail;
  try {
    vacancy = await getVacancy(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    if (error instanceof ApiError) {
      return (
        <main className="mx-auto w-full max-w-4xl px-4 py-8">
          <ErrorState message={error.message} retryHref={`/vacancies/${id}`} />
        </main>
      );
    }
    throw error;
  }

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-8">
      <Button variant="ghost" size="sm" asChild className="-ml-2">
        <Link href="/vacancies">
          <ArrowLeft className="size-4" />
          К списку вакансий
        </Link>
      </Button>

      <Card>
        <CardHeader className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={vacancy.seniority === "INTERN" ? "default" : "secondary"}>
              {seniorityLabels[vacancy.seniority]}
            </Badge>
            <Badge variant="outline">{remoteLabels[vacancy.remoteType]}</Badge>
            {vacancy.profession && (
              <Badge variant="outline">
                {vacancy.professionName ?? humanizeCode(vacancy.profession)}
              </Badge>
            )}
            {vacancy.status !== "ACTIVE" && <Badge variant="destructive">{vacancy.status}</Badge>}
          </div>

          <CardTitle className="text-2xl leading-tight">{vacancy.title}</CardTitle>

          <div className="text-muted-foreground flex flex-wrap gap-x-5 gap-y-2 text-sm">
            <span className="flex items-center gap-1.5">
              <Building2 className="size-4" />
              {vacancy.company ?? "Компания не указана"}
            </span>
            {vacancy.location && (
              <span className="flex items-center gap-1.5">
                <MapPin className="size-4" />
                {vacancy.location}
              </span>
            )}
            <span className="flex items-center gap-1.5">
              <Wallet className="size-4" />
              {formatSalary(
                vacancy.salaryFrom,
                vacancy.salaryTo,
                vacancy.currency,
                vacancy.salaryPeriod,
              )}
            </span>
            <span className="flex items-center gap-1.5">
              <CalendarDays className="size-4" />
              {formatDate(vacancy.publishedAt ?? vacancy.firstSeenAt)}
            </span>
          </div>
        </CardHeader>

        <CardContent className="space-y-6">
          <section className="space-y-2">
            <h2 className="text-sm font-semibold tracking-wide uppercase">Описание</h2>
            <p className="text-sm leading-6 whitespace-pre-line">
              {vacancy.description ?? "Источник не передал описание вакансии."}
            </p>
          </section>

          {vacancy.skills.length > 0 && (
            <section className="space-y-2">
              <h2 className="text-sm font-semibold tracking-wide uppercase">Навыки</h2>
              <div className="flex flex-wrap gap-2">
                {vacancy.skills.map((skill) => (
                  <Badge key={skill.code} variant="secondary">
                    {skill.name}
                  </Badge>
                ))}
              </div>
            </section>
          )}

          <section className="border-t pt-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="text-muted-foreground text-xs">
                Источник: {vacancy.sourceName ?? vacancy.source ?? "неизвестен"} · впервые найдена{" "}
                {formatDate(vacancy.firstSeenAt)}
              </div>
              {vacancy.sourceUrl && (
                <Button asChild>
                  <a href={vacancy.sourceUrl} target="_blank" rel="noreferrer">
                    Открыть оригинал
                    <ExternalLink className="size-4" />
                  </a>
                </Button>
              )}
            </div>
          </section>
        </CardContent>
      </Card>
    </main>
  );
}
