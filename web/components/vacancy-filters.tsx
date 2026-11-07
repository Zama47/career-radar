"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Search, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { buildQuery } from "@/lib/query";
import type { Profession } from "@/lib/types";

export type VacancyFilterValues = {
  q: string;
  profession: string;
  seniority: string;
  remote: string;
  salaryMin: string;
  sort: string;
};

const ALL = "ALL";

const seniorityOptions = [
  { value: "INTERN", label: "Стажёр" },
  { value: "JUNIOR", label: "Junior" },
  { value: "MIDDLE", label: "Middle" },
  { value: "SENIOR", label: "Senior" },
  { value: "LEAD", label: "Lead" },
];

const remoteOptions = [
  { value: "OFFICE", label: "Офис" },
  { value: "HYBRID", label: "Гибрид" },
  { value: "REMOTE", label: "Удалённо" },
];

const sortOptions = [
  { value: "publishedAt,desc", label: "Сначала новые" },
  { value: "firstSeenAt,desc", label: "Сначала добавленные" },
  { value: "salaryFrom,desc", label: "Высокая зарплата" },
];

export function VacancyFilters({
  professions,
  current,
}: {
  professions: Profession[];
  current: VacancyFilterValues;
}) {
  const router = useRouter();
  const [q, setQ] = useState(current.q);
  const [profession, setProfession] = useState(current.profession || ALL);
  const [seniority, setSeniority] = useState(current.seniority || ALL);
  const [remote, setRemote] = useState(current.remote || ALL);
  const [salaryMin, setSalaryMin] = useState(current.salaryMin);
  const [sort, setSort] = useState(current.sort || "publishedAt,desc");

  function apply(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = buildQuery({
      q: q.trim(),
      profession: profession === ALL ? "" : profession,
      seniority: seniority === ALL ? "" : seniority,
      remote: remote === ALL ? "" : remote,
      salaryMin: salaryMin.trim(),
      sort,
    });
    router.push(`/vacancies${query}`);
  }

  function reset() {
    setQ("");
    setProfession(ALL);
    setSeniority(ALL);
    setRemote(ALL);
    setSalaryMin("");
    setSort("publishedAt,desc");
    router.push("/vacancies");
  }

  return (
    <Card>
      <CardContent>
        <form onSubmit={apply} className="grid gap-3 lg:grid-cols-12">
          <div className="lg:col-span-4">
            <Input
              aria-label="Поиск"
              placeholder="Поиск по названию, компании, описанию"
              value={q}
              onChange={(event) => setQ(event.target.value)}
            />
          </div>

          <div className="lg:col-span-3">
            <Select value={profession} onValueChange={setProfession}>
              <SelectTrigger className="w-full" aria-label="Профессия">
                <SelectValue placeholder="Все профессии" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Все профессии</SelectItem>
                {professions.map((item) => (
                  <SelectItem key={item.code} value={item.code}>
                    {item.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="lg:col-span-2">
            <Select value={seniority} onValueChange={setSeniority}>
              <SelectTrigger className="w-full" aria-label="Грейд">
                <SelectValue placeholder="Все грейды" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Все грейды</SelectItem>
                {seniorityOptions.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="lg:col-span-2">
            <Select value={remote} onValueChange={setRemote}>
              <SelectTrigger className="w-full" aria-label="Формат работы">
                <SelectValue placeholder="Любой формат" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Любой формат</SelectItem>
                {remoteOptions.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="lg:col-span-2">
            <Input
              aria-label="Зарплата от"
              inputMode="numeric"
              placeholder="Зарплата от"
              value={salaryMin}
              onChange={(event) => setSalaryMin(event.target.value.replace(/[^\d]/g, ""))}
            />
          </div>

          <div className="lg:col-span-2">
            <Select value={sort} onValueChange={setSort}>
              <SelectTrigger className="w-full" aria-label="Сортировка">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {sortOptions.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex gap-2 lg:col-span-10 lg:col-start-3 lg:justify-end">
            <Button type="button" variant="ghost" onClick={reset}>
              <X className="size-4" />
              Сбросить
            </Button>
            <Button type="submit">
              <Search className="size-4" />
              Применить
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
