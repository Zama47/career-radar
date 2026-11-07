import { NextResponse } from "next/server";

import { API_URL, TOKEN_COOKIE } from "@/lib/config";
import type { LoginResponse } from "@/lib/types";

export const dynamic = "force-dynamic";

type LoginPayload = {
  email?: unknown;
  password?: unknown;
};

/**
 * Proxies login to core-api and stores the JWT in an httpOnly cookie, so the token never
 * reaches client JavaScript.
 */
export async function POST(request: Request) {
  let payload: LoginPayload;
  try {
    payload = (await request.json()) as LoginPayload;
  } catch {
    return NextResponse.json({ error: "Некорректный запрос" }, { status: 400 });
  }

  if (typeof payload.email !== "string" || typeof payload.password !== "string") {
    return NextResponse.json({ error: "Укажите email и пароль" }, { status: 400 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}/api/v1/auth/login`, {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ email: payload.email, password: payload.password }),
    });
  } catch {
    return NextResponse.json(
      { error: "core-api недоступен. Проверьте, что PostgreSQL и core-api запущены." },
      { status: 502 },
    );
  }

  if (upstream.status === 401) {
    return NextResponse.json({ error: "Неверный email или пароль" }, { status: 401 });
  }
  if (!upstream.ok) {
    return NextResponse.json(
      { error: `core-api отклонил вход (${upstream.status})` },
      { status: 502 },
    );
  }

  const session = (await upstream.json()) as LoginResponse;
  const response = NextResponse.json({ ok: true });
  response.cookies.set({
    name: TOKEN_COOKIE,
    value: session.accessToken,
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: Math.max(session.expiresIn, 60),
  });
  return response;
}
