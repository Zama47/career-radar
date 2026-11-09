"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { FileText, Plus, Save, Trash2, Upload } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { formatDate } from "@/lib/format";
import type { ResumeDetail } from "@/lib/types";
import { cn } from "@/lib/utils";

const MAX_PDF_BYTES = 10 * 1024 * 1024;

type ApiProblem = {
  detail?: string;
  title?: string;
  error?: string;
};

async function callApi<T>(url: string, method: string, body?: unknown): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = (await response.json().catch(() => null)) as (T & ApiProblem) | null;
  if (!response.ok) {
    throw new Error(
      data?.detail ?? data?.error ?? data?.title ?? `Запрос завершился с кодом ${response.status}`,
    );
  }
  return data as T;
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(new Error("Не удалось прочитать PDF"));
    reader.readAsDataURL(file);
  });
}

export function ResumeManager({ initial }: { initial: ResumeDetail[] }) {
  const router = useRouter();
  const [resumes, setResumes] = useState(initial);
  const [selectedId, setSelectedId] = useState<string | null>(initial[0]?.id ?? null);
  const [title, setTitle] = useState(initial[0]?.title ?? "");
  const [contentText, setContentText] = useState(initial[0]?.contentText ?? "");
  const [contentJsonText, setContentJsonText] = useState(
    JSON.stringify(initial[0]?.contentJson ?? {}, null, 2),
  );
  const [isMaster, setIsMaster] = useState(initial[0]?.isMaster ?? initial.length === 0);
  const [pdfBase64, setPdfBase64] = useState<string | null>(null);
  const [pdfName, setPdfName] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    setResumes(initial);
    if (selectedId == null) {
      return;
    }
    const current = initial.find((resume) => resume.id === selectedId);
    if (current) {
      fillForm(current);
    }
    // fillForm is stable local state wiring; re-running only when server data changes is intended.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initial, selectedId]);

  function fillForm(resume: ResumeDetail) {
    setTitle(resume.title);
    setContentText(resume.contentText);
    setContentJsonText(JSON.stringify(resume.contentJson ?? {}, null, 2));
    setIsMaster(resume.isMaster);
    setPdfBase64(null);
    setPdfName(null);
  }

  function selectResume(id: string) {
    const resume = resumes.find((item) => item.id === id);
    if (!resume) return;
    setSelectedId(id);
    fillForm(resume);
    setError(null);
    setNotice(null);
  }

  function startNew() {
    setSelectedId(null);
    setTitle("");
    setContentText("");
    setContentJsonText("{}");
    setIsMaster(resumes.length === 0);
    setPdfBase64(null);
    setPdfName(null);
    setError(null);
    setNotice(null);
  }

  async function handlePdf(file: File | undefined) {
    setError(null);
    if (!file) {
      setPdfBase64(null);
      setPdfName(null);
      return;
    }
    if (file.type !== "application/pdf") {
      setError("Выберите PDF-файл");
      return;
    }
    if (file.size > MAX_PDF_BYTES) {
      setError("PDF больше 10 МБ");
      return;
    }
    try {
      setPdfBase64(await fileToDataUrl(file));
      setPdfName(file.name);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось прочитать PDF");
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);

    if (!title.trim() || !contentText.trim()) {
      setError("Заполните название и текст резюме");
      return;
    }

    let contentJson: unknown;
    try {
      contentJson = JSON.parse(contentJsonText);
    } catch {
      setError("contentJson — некорректный JSON");
      return;
    }
    if (typeof contentJson !== "object" || contentJson === null || Array.isArray(contentJson)) {
      setError("contentJson должен быть JSON-объектом");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        title: title.trim(),
        contentJson,
        contentText,
        isMaster,
        ...(pdfBase64 ? { pdfBase64 } : {}),
      };
      const saved = selectedId
        ? await callApi<ResumeDetail>(`/api/resumes/${selectedId}`, "PUT", payload)
        : await callApi<ResumeDetail>("/api/resumes", "POST", payload);
      setSelectedId(saved.id);
      fillForm(saved);
      setNotice("Резюме сохранено");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось сохранить резюме");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!selectedId) return;
    if (!window.confirm("Удалить резюме и загруженный PDF?")) return;
    setError(null);
    setNotice(null);
    try {
      await callApi(`/api/resumes/${selectedId}`, "DELETE");
      startNew();
      setNotice("Резюме удалено");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось удалить резюме");
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
      <aside className="space-y-2">
        <Button type="button" className="w-full" onClick={startNew}>
          <Plus className="size-4" />
          Новое резюме
        </Button>

        {resumes.length === 0 ? (
          <p className="text-muted-foreground rounded-md border border-dashed p-4 text-sm">
            Резюме пока нет. Создайте мастер-резюме — оно понадобится для fit-score и писем.
          </p>
        ) : (
          resumes.map((resume) => (
            <button
              key={resume.id}
              type="button"
              onClick={() => selectResume(resume.id)}
              className={cn(
                "w-full rounded-md border p-3 text-left transition-colors hover:bg-accent",
                selectedId === resume.id && "border-primary bg-accent",
              )}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="truncate text-sm font-medium">{resume.title}</span>
                {resume.isMaster && <Badge>master</Badge>}
              </div>
              <div className="text-muted-foreground mt-1 flex items-center gap-1 text-xs">
                <FileText className="size-3" />
                {resume.hasPdf ? "PDF загружен" : "без PDF"} · {formatDate(resume.updatedAt)}
              </div>
            </button>
          ))
        )}
      </aside>

      <Card>
        <CardHeader>
          <CardTitle>{selectedId ? "Редактирование резюме" : "Новое резюме"}</CardTitle>
          <CardDescription>
            contentJson — структурированные данные, contentText — то, что видит LLM. Оба поля
            обязательны.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="resume-title">
                Название
              </label>
              <Input
                id="resume-title"
                value={title}
                maxLength={255}
                placeholder="Android Developer — master"
                onChange={(event) => setTitle(event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="resume-text">
                Текст резюме (contentText)
              </label>
              <textarea
                id="resume-text"
                value={contentText}
                rows={10}
                placeholder="Опыт, проекты, стек, ссылки…"
                className="border-input bg-background focus-visible:border-ring focus-visible:ring-ring/50 w-full rounded-md border px-3 py-2 text-sm outline-none focus-visible:ring-[3px]"
                onChange={(event) => setContentText(event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="resume-json">
                Структурированное резюме (contentJson)
              </label>
              <textarea
                id="resume-json"
                value={contentJsonText}
                rows={10}
                spellCheck={false}
                className="border-input bg-muted/40 focus-visible:border-ring focus-visible:ring-ring/50 w-full rounded-md border px-3 py-2 font-mono text-xs outline-none focus-visible:ring-[3px]"
                onChange={(event) => setContentJsonText(event.target.value)}
              />
            </div>

            <div className="flex flex-wrap items-center gap-4">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={isMaster}
                  className="size-4 accent-primary"
                  onChange={(event) => setIsMaster(event.target.checked)}
                />
                Мастер-резюме
              </label>

              <label className="border-input hover:bg-accent flex cursor-pointer items-center gap-2 rounded-md border px-3 py-1.5 text-sm">
                <Upload className="size-4" />
                {pdfName ?? "Загрузить PDF"}
                <input
                  type="file"
                  accept="application/pdf"
                  className="hidden"
                  onChange={(event) => handlePdf(event.target.files?.[0])}
                />
              </label>

              {selectedId && resumes.find((item) => item.id === selectedId)?.hasPdf && (
                <a
                  href={`/api/resumes/${selectedId}/file`}
                  target="_blank"
                  rel="noreferrer"
                  className="text-primary text-sm underline-offset-4 hover:underline"
                >
                  Открыть PDF
                </a>
              )}
            </div>

            {error && <p className="text-destructive text-sm">{error}</p>}
            {notice && <p className="text-sm text-emerald-600">{notice}</p>}

            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={saving}>
                <Save className="size-4" />
                {saving ? "Сохраняем…" : "Сохранить"}
              </Button>
              {selectedId && (
                <Button type="button" variant="outline" onClick={handleDelete}>
                  <Trash2 className="size-4" />
                  Удалить
                </Button>
              )}
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
