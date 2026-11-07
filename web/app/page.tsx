import { Activity, ArrowUpRight, Bot, Database, Mail, Radar as RadarIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { API_URL, getApiStatus } from "@/lib/api";

export const dynamic = "force-dynamic";

const upcoming = [
  { icon: Database, label: "PostgreSQL + Flyway schema and sources registry" },
  { icon: RadarIcon, label: "hh.ru collector with normalization and deduplication" },
  { icon: Bot, label: "LLM fit-score and resume tailoring" },
  { icon: Mail, label: "Read-only inbox linked to applications" },
];

export default async function HomePage() {
  const api = await getApiStatus();

  return (
    <main className="relative mx-auto flex min-h-dvh w-full max-w-5xl flex-col gap-10 px-6 py-16">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-72 bg-[radial-gradient(ellipse_at_top,oklch(0.92_0_0),transparent_65%)] dark:bg-[radial-gradient(ellipse_at_top,oklch(0.3_0_0),transparent_65%)]"
      />

      <header className="flex flex-col gap-4">
        <div className="bg-primary text-primary-foreground flex size-11 items-center justify-center rounded-xl">
          <RadarIcon className="size-6" />
        </div>
        <div className="space-y-2">
          <h1 className="text-4xl font-semibold tracking-tight">Career Radar</h1>
          <p className="text-muted-foreground max-w-2xl">
            Personal job-search operating system. Stage 0: the skeleton is up — core API,
            collector, web, PostgreSQL and Redis.
          </p>
        </div>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Activity className="size-4" />
            Core API status
          </CardTitle>
          <CardDescription>
            Live check of <code className="font-mono text-xs">GET /api/v1/ping</code> at{" "}
            <code className="font-mono text-xs">{API_URL}</code>
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span
              className={
                api.reachable
                  ? "size-2.5 rounded-full bg-emerald-500"
                  : "bg-destructive size-2.5 rounded-full"
              }
            />
            <span className="text-sm font-medium">
              {api.reachable ? `reachable · ${api.status}` : "unreachable"}
            </span>
          </div>
          <Button variant="outline" size="sm" asChild>
            <a href={`${API_URL}/swagger-ui.html`} target="_blank" rel="noreferrer">
              OpenAPI docs
              <ArrowUpRight className="size-4" />
            </a>
          </Button>
        </CardContent>
      </Card>

      <section className="space-y-4">
        <h2 className="text-sm font-medium tracking-wide uppercase">Coming next</h2>
        <ul className="grid gap-3 sm:grid-cols-2">
          {upcoming.map(({ icon: Icon, label }) => (
            <li
              key={label}
              className="bg-card flex items-start gap-3 rounded-lg border p-4 text-sm"
            >
              <Icon className="text-muted-foreground mt-0.5 size-4" />
              <span>{label}</span>
            </li>
          ))}
        </ul>
      </section>

      <footer className="text-muted-foreground mt-auto text-xs">
        Career Radar 0.1.0 · personal tool, single user · no billing, no multi-tenancy
      </footer>
    </main>
  );
}
