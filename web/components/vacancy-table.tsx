import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate, formatSalary, humanizeCode, remoteLabels, seniorityLabels } from "@/lib/format";
import type { VacancyListItem } from "@/lib/types";

export function VacancyTable({
  items,
  professionNames,
}: {
  items: VacancyListItem[];
  professionNames: Record<string, string>;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Вакансия</TableHead>
          <TableHead className="hidden md:table-cell">Профессия</TableHead>
          <TableHead>Грейд</TableHead>
          <TableHead className="hidden lg:table-cell">Формат</TableHead>
          <TableHead>Зарплата</TableHead>
          <TableHead className="hidden sm:table-cell">Опубликована</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((vacancy) => (
          <TableRow key={vacancy.id}>
            <TableCell className="max-w-[22rem] whitespace-normal">
              <Link
                href={`/vacancies/${vacancy.id}`}
                className="font-medium underline-offset-4 hover:underline"
              >
                {vacancy.title}
              </Link>
              <div className="text-muted-foreground text-xs">
                {vacancy.company ?? "Компания не указана"}
                {vacancy.location ? ` · ${vacancy.location}` : ""}
              </div>
            </TableCell>
            <TableCell className="hidden md:table-cell">
              {vacancy.profession
                ? (professionNames[vacancy.profession] ?? humanizeCode(vacancy.profession))
                : "—"}
            </TableCell>
            <TableCell>
              <Badge variant={vacancy.seniority === "INTERN" ? "default" : "secondary"}>
                {seniorityLabels[vacancy.seniority]}
              </Badge>
            </TableCell>
            <TableCell className="hidden lg:table-cell">
              {remoteLabels[vacancy.remoteType]}
            </TableCell>
            <TableCell className="text-sm">
              {formatSalary(
                vacancy.salaryFrom,
                vacancy.salaryTo,
                vacancy.currency,
                vacancy.salaryPeriod,
              )}
            </TableCell>
            <TableCell className="text-muted-foreground hidden text-sm sm:table-cell">
              {formatDate(vacancy.publishedAt ?? vacancy.firstSeenAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
