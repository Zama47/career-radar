"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Filter, RotateCcw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { periodOptions } from "@/lib/period";
import { buildQuery } from "@/lib/query";
import type { Profession } from "@/lib/types";

export type AnalyticsFilterValues = {
  profession: string;
  seniority: string;
  remote: string;
  period: string;
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

export function AnalyticsFilters({
  professions,
  current,
  basePath,
}: {
  professions: Profession[];
  current: AnalyticsFilterValues;
  basePath: string;
}) {
  const router = useRouter();
  const [profession, setProfession] = useState(current.profession || ALL);
  const [seniority, setSeniority] = useState(current.seniority || ALL);
  const [remote, setRemote] = useState(current.remote || ALL);
  const [period, setPeriod] = useState(current.period || "90");

  function apply(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = buildQuery({
      profession: profession === ALL ? "" : profession,
      seniority: seniority === ALL ? "" : seniority,
      remote: remote === ALL ? "" : remote,
      period,
    });
    router.push(`${basePath}${query}`);
  }

  function reset() {
    setProfession(ALL);
    setSeniority(ALL);
    setRemote(ALL);
    setPeriod("90");
    router.push(basePath);
  }

  return (
    <Card>
      <CardContent>
        <form onSubmit={apply} className="grid gap-3 lg:grid-cols-12">
          <div className="lg:col-span-4">
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

          <div className="lg:col-span-3">
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

          <div className="lg:col-span-3">
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
            <Select value={period} onValueChange={setPeriod}>
              <SelectTrigger className="w-full" aria-label="Период">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {periodOptions.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex gap-2 lg:col-span-12 lg:justify-end">
            <Button type="button" variant="ghost" onClick={reset}>
              <RotateCcw className="size-4" />
              Сбросить
            </Button>
            <Button type="submit">
              <Filter className="size-4" />
              Применить
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
