const DEFAULT_API_URL = "http://localhost:8080";

export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? DEFAULT_API_URL).replace(/\/+$/, "");

export type PingResponse = {
  status: string;
};

export type ApiStatus = {
  reachable: boolean;
  status: string;
};

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...init?.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`core-api ${path} returned ${response.status}`);
  }

  return (await response.json()) as T;
}

/** Reads core-api liveness. Never throws: the home page renders an "unreachable" state instead. */
export async function getApiStatus(): Promise<ApiStatus> {
  try {
    const payload = await apiFetch<PingResponse>("/api/v1/ping");
    return { reachable: true, status: payload.status };
  } catch {
    return { reachable: false, status: "unreachable" };
  }
}
