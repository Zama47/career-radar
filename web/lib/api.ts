import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import { API_URL, TOKEN_COOKIE } from "@/lib/config";
import { buildQuery } from "@/lib/query";
import type { Dashboard, Profession, VacancyDetail, VacancyPage } from "@/lib/types";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

/**
 * Server-only core-api client. Reads the httpOnly token cookie, forwards it as a Bearer
 * token and redirects to /login when the session is gone.
 */
async function authorizedFetch(path: string, init?: RequestInit): Promise<Response> {
  const token = (await cookies()).get(TOKEN_COOKIE)?.value;
  if (!token) redirect("/login");

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...init,
      cache: "no-store",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiError(
      0,
      "core-api недоступен. Проверьте, что PostgreSQL и core-api запущены.",
    );
  }

  if (response.status === 401) redirect("/login");

  if (!response.ok) {
    let detail = `Запрос завершился с кодом ${response.status}`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      detail = problem.detail ?? problem.title ?? detail;
    } catch {
      // Non-JSON error body: keep the status-based message.
    }
    throw new ApiError(response.status, detail);
  }

  return response;
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await authorizedFetch(path);
  return (await response.json()) as T;
}

export type VacancyFilters = {
  q?: string;
  profession?: string;
  seniority?: string;
  remote?: string;
  salaryMin?: string;
  sort?: string;
  page?: number;
  size?: number;
};

export function getVacancies(filters: VacancyFilters): Promise<VacancyPage> {
  return apiGet<VacancyPage>(`/api/v1/vacancies${buildQuery({ ...filters })}`);
}

export function getDashboard(): Promise<Dashboard> {
  return apiGet<Dashboard>("/api/v1/dashboard");
}

export function getRecentVacancies(size = 5): Promise<VacancyPage> {
  return apiGet<VacancyPage>(
    `/api/v1/vacancies${buildQuery({ page: 0, size, sort: "publishedAt,desc" })}`,
  );
}

export function getVacancy(id: string): Promise<VacancyDetail> {
  return apiGet<VacancyDetail>(`/api/v1/vacancies/${encodeURIComponent(id)}`);
}

export function getProfessions(): Promise<Profession[]> {
  return apiGet<Profession[]>("/api/v1/professions");
}
