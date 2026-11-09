import { ResumeManager } from "@/components/resume-manager";
import { ErrorState } from "@/components/states";
import { ApiError, getResume, getResumes } from "@/lib/api";
import type { ResumeDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function ResumePage() {
  let resumes: ResumeDetail[];
  try {
    const summaries = await getResumes();
    resumes = await Promise.all(summaries.map((resume) => getResume(resume.id)));
  } catch (error) {
    if (error instanceof ApiError) {
      return (
        <main className="mx-auto w-full max-w-6xl px-4 py-8">
          <ErrorState message={error.message} retryHref="/resume" />
        </main>
      );
    }
    throw error;
  }

  return (
    <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Резюме</h1>
        <p className="text-muted-foreground text-sm">
          Мастер-резюме — основа для fit-score, сопроводительных писем и адаптации под вакансию.
          Все AI-результаты — черновики.
        </p>
      </header>

      <ResumeManager initial={resumes} />
    </main>
  );
}
