"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  ClipboardCopy,
  Download,
  FilePenLine,
  RefreshCw,
  Sparkles,
  Target,
  Wand2,
  X,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type {
  CoverLetterDraft,
  FitResult,
  FitVerdict,
  ResumeSummary,
  TailorDraft,
} from "@/lib/types";
import { cn } from "@/lib/utils";

const verdictLabels: Record<FitVerdict, string> = {
  strong_match: "Сильное совпадение",
  good_match: "Хорошее совпадение",
  partial_match: "Частичное совпадение",
  weak_match: "Слабое совпадение",
};

const tones = [
  { value: "professional", label: "Профессиональный" },
  { value: "friendly", label: "Дружелюбный" },
  { value: "concise", label: "Краткий" },
];

type ApiProblem = { detail?: string; title?: string; error?: string };

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = (await response.json().catch(() => null)) as (T & ApiProblem) | null;
  if (!response.ok) {
    throw new Error(
      data?.detail ?? data?.error ?? data?.title ?? `Запрос завершился с кодом ${response.status}`,
    );
  }
  return data as T;
}

function scoreClass(score: number): string {
  if (score >= 80) return "text-emerald-600";
  if (score >= 60) return "text-amber-600";
  return "text-red-600";
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div className="bg-background max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-lg border p-5 shadow-xl">
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 className="text-lg font-semibold">{title}</h2>
          <Button type="button" variant="ghost" size="icon" onClick={onClose} aria-label="Закрыть">
            <X className="size-4" />
          </Button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function FitPanel({
  vacancyId,
  resumes,
}: {
  vacancyId: string;
  resumes: ResumeSummary[];
}) {
  const defaultResume = resumes.find((resume) => resume.isMaster) ?? resumes[0];
  const [resumeId, setResumeId] = useState(defaultResume?.id ?? "");
  const [fit, setFit] = useState<FitResult | null>(null);
  const [fitLoading, setFitLoading] = useState(false);
  const [letter, setLetter] = useState<CoverLetterDraft | null>(null);
  const [letterOpen, setLetterOpen] = useState(false);
  const [letterLoading, setLetterLoading] = useState(false);
  const [tone, setTone] = useState("professional");
  const [copied, setCopied] = useState(false);
  const [tailor, setTailor] = useState<TailorDraft | null>(null);
  const [tailorOpen, setTailorOpen] = useState(false);
  const [tailorLoading, setTailorLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (resumes.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Target className="size-4" />
            Проверка под вакансию
          </CardTitle>
          <CardDescription>
            Добавьте мастер-резюме, чтобы увидеть fit-score, недостающие навыки и черновик письма.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild>
            <Link href="/resume">Создать резюме</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  async function runFit() {
    setFitLoading(true);
    setError(null);
    try {
      setFit(await postJson<FitResult>(`/api/vacancies/${vacancyId}/fit`, { resumeId }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось посчитать fit-score");
    } finally {
      setFitLoading(false);
    }
  }

  async function generateLetter(forceRefresh: boolean) {
    setLetterOpen(true);
    setLetterLoading(true);
    setError(null);
    setCopied(false);
    try {
      setLetter(
        await postJson<CoverLetterDraft>(`/api/vacancies/${vacancyId}/cover-letter`, {
          resumeId,
          tone,
          forceRefresh,
        }),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось сгенерировать письмо");
    } finally {
      setLetterLoading(false);
    }
  }

  async function runTailor(forceRefresh: boolean) {
    setTailorOpen(true);
    setTailorLoading(true);
    setError(null);
    try {
      setTailor(
        await postJson<TailorDraft>(`/api/resumes/${resumeId}/tailor`, {
          vacancyId,
          forceRefresh,
        }),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось адаптировать резюме");
    } finally {
      setTailorLoading(false);
    }
  }

  async function copyLetter() {
    if (!letter) return;
    try {
      await navigator.clipboard.writeText(`${letter.subject}\n\n${letter.text}`);
      setCopied(true);
    } catch {
      setError("Браузер не разрешил доступ к буферу обмена");
    }
  }

  function downloadLetter() {
    if (!letter) return;
    const blob = new Blob([`${letter.subject}\n\n${letter.text}`], {
      type: "text/plain;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "cover-letter.txt";
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Target className="size-4" />
          Проверка под вакансию
        </CardTitle>
        <CardDescription>
          Fit-score, недостающие навыки и черновики. Ничего не отправляется автоматически.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <label className="min-w-[220px] flex-1 space-y-1">
            <span className="text-muted-foreground text-xs">Резюме</span>
            <select
              value={resumeId}
              className="border-input bg-background focus-visible:border-ring focus-visible:ring-ring/50 h-9 w-full rounded-md border px-3 text-sm outline-none focus-visible:ring-[3px]"
              onChange={(event) => {
                setResumeId(event.target.value);
                setFit(null);
                setLetter(null);
                setTailor(null);
              }}
            >
              {resumes.map((resume) => (
                <option key={resume.id} value={resume.id}>
                  {resume.title}
                  {resume.isMaster ? " (master)" : ""}
                </option>
              ))}
            </select>
          </label>
          <Button type="button" onClick={runFit} disabled={fitLoading || !resumeId}>
            <Sparkles className="size-4" />
            {fitLoading ? "Считаем…" : "Проверить fit"}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => generateLetter(false)}
            disabled={letterLoading || !resumeId}
          >
            <Wand2 className="size-4" />
            Письмо
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => runTailor(false)}
            disabled={tailorLoading || !resumeId}
          >
            <FilePenLine className="size-4" />
            Адаптировать
          </Button>
        </div>

        {error && <p className="text-destructive text-sm">{error}</p>}

        {fit && (
          <div className="space-y-3 rounded-lg border p-4">
            <div className="flex flex-wrap items-center gap-3">
              <span className={cn("text-3xl font-semibold tabular-nums", scoreClass(fit.score))}>
                {fit.score}
              </span>
              <Badge variant="secondary">{verdictLabels[fit.verdict] ?? fit.verdict}</Badge>
              {fit.cached && <Badge variant="outline">из кэша</Badge>}
              <span className="text-muted-foreground text-xs">
                {fit.model} · {fit.tokens.input} → {fit.tokens.output} токенов
              </span>
            </div>

            <div className="h-2 overflow-hidden rounded-full bg-muted">
              <div
                className={cn(
                  "h-full rounded-full",
                  fit.score >= 80
                    ? "bg-emerald-500"
                    : fit.score >= 60
                      ? "bg-amber-500"
                      : "bg-red-500",
                )}
                style={{ width: `${Math.max(0, Math.min(100, fit.score))}%` }}
              />
            </div>

            {fit.strengths.length > 0 && (
              <div className="space-y-1">
                <p className="text-xs font-medium tracking-wide uppercase">Сильные стороны</p>
                <div className="flex flex-wrap gap-1.5">
                  {fit.strengths.map((item) => (
                    <Badge key={item} variant="secondary">
                      {item}
                    </Badge>
                  ))}
                </div>
              </div>
            )}

            {fit.missing.length > 0 && (
              <div className="space-y-1">
                <p className="text-xs font-medium tracking-wide uppercase">Чего не хватает</p>
                <div className="flex flex-wrap gap-1.5">
                  {fit.missing.map((item) => (
                    <Badge key={item} variant="outline" className="border-amber-500/50">
                      {item}
                    </Badge>
                  ))}
                </div>
              </div>
            )}

            {fit.reasons.length > 0 && (
              <ul className="text-muted-foreground list-disc space-y-1 pl-5 text-sm">
                {fit.reasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        {letterOpen && (
          <Modal title="Черновик сопроводительного письма" onClose={() => setLetterOpen(false)}>
            <div className="space-y-4">
              <div className="flex flex-wrap items-end gap-2">
                <label className="space-y-1">
                  <span className="text-muted-foreground text-xs">Тон</span>
                  <select
                    value={tone}
                    className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm"
                    onChange={(event) => setTone(event.target.value)}
                  >
                    {tones.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => generateLetter(true)}
                  disabled={letterLoading}
                >
                  <RefreshCw className="size-4" />
                  Перегенерировать
                </Button>
              </div>

              {error && <p className="text-destructive text-sm">{error}</p>}

              {letterLoading && (
                <p className="text-muted-foreground text-sm">Генерируем черновик…</p>
              )}

              {letter && !letterLoading && (
                <>
                  <div>
                    <p className="text-muted-foreground text-xs tracking-wide uppercase">Тема</p>
                    <p className="font-medium">{letter.subject}</p>
                  </div>
                  <textarea
                    readOnly
                    rows={12}
                    value={letter.text}
                    className="border-input bg-muted/40 w-full rounded-md border px-3 py-2 text-sm"
                  />
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" size="sm" onClick={copyLetter}>
                      <ClipboardCopy className="size-4" />
                      {copied ? "Скопировано" : "Скопировать"}
                    </Button>
                    <Button type="button" size="sm" variant="outline" onClick={downloadLetter}>
                      <Download className="size-4" />
                      Сохранить .txt
                    </Button>
                  </div>
                  <p className="text-muted-foreground text-xs">
                    Черновик сохранён как вариант резюме, но не отправлен. Проверьте факты перед
                    использованием.
                  </p>
                </>
              )}
            </div>
          </Modal>
        )}

        {tailorOpen && (
          <Modal title="Адаптация резюме под вакансию" onClose={() => setTailorOpen(false)}>
            <div className="space-y-4">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => runTailor(true)}
                disabled={tailorLoading}
              >
                <RefreshCw className="size-4" />
                Перегенерировать
              </Button>

              {error && <p className="text-destructive text-sm">{error}</p>}
              {tailorLoading && (
                <p className="text-muted-foreground text-sm">Адаптируем резюме…</p>
              )}

              {tailor && !tailorLoading && (
                <>
                  {tailor.warnings.length > 0 && (
                    <div className="rounded-md border border-amber-500/50 bg-amber-500/5 p-3 text-sm">
                      <p className="mb-1 font-medium">Проверьте вручную</p>
                      <ul className="list-disc space-y-1 pl-5">
                        {tailor.warnings.map((warning) => (
                          <li key={warning}>{warning}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {tailor.diff.length > 0 && (
                    <div>
                      <p className="text-xs font-medium tracking-wide uppercase">Что изменено</p>
                      <ul className="list-disc space-y-1 pl-5 text-sm">
                        {tailor.diff.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  <textarea
                    readOnly
                    rows={12}
                    value={JSON.stringify(tailor.contentJson, null, 2)}
                    className="border-input bg-muted/40 w-full rounded-md border px-3 py-2 font-mono text-xs"
                  />
                  <p className="text-muted-foreground text-xs">
                    Мастер-резюме не изменено. Скопируйте JSON на страницу «Резюме», если согласны с
                    правками.
                  </p>
                </>
              )}
            </div>
          </Modal>
        )}
      </CardContent>
    </Card>
  );
}
