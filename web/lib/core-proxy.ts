import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { API_URL, TOKEN_COOKIE } from "@/lib/config";

/**
 * Server-side proxy to core-api for browser mutations. The JWT stays in the httpOnly
 * cookie and is never exposed to client JavaScript.
 */
async function authToken(): Promise<string | null> {
  return (await cookies()).get(TOKEN_COOKIE)?.value ?? null;
}

function unavailable(): NextResponse {
  return NextResponse.json(
    { error: "core-api недоступен. Проверьте, что PostgreSQL и core-api запущены." },
    { status: 502 },
  );
}

export async function proxyJson(
  path: string,
  method: string,
  body?: unknown,
): Promise<NextResponse> {
  const token = await authToken();
  if (!token) {
    return NextResponse.json({ error: "Требуется вход" }, { status: 401 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}${path}`, {
      method,
      cache: "no-store",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    return unavailable();
  }

  const text = await upstream.text();
  const nullBodyStatus =
    upstream.status === 204 || upstream.status === 205 || upstream.status === 304;
  return new NextResponse(nullBodyStatus ? null : text, {
    status: upstream.status,
    headers: {
      "Content-Type": upstream.headers.get("content-type") ?? "application/json",
    },
  });
}

export async function proxyFile(path: string): Promise<NextResponse> {
  const token = await authToken();
  if (!token) {
    return NextResponse.json({ error: "Требуется вход" }, { status: 401 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}${path}`, {
      cache: "no-store",
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    return unavailable();
  }

  if (!upstream.ok) {
    const text = await upstream.text();
    return new NextResponse(text, {
      status: upstream.status,
      headers: {
        "Content-Type": upstream.headers.get("content-type") ?? "application/json",
      },
    });
  }

  return new NextResponse(await upstream.arrayBuffer(), {
    status: upstream.status,
    headers: {
      "Content-Type": upstream.headers.get("content-type") ?? "application/pdf",
      "Content-Disposition":
        upstream.headers.get("content-disposition") ?? "inline; filename=\"resume.pdf\"",
    },
  });
}
