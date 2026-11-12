import { NextResponse } from "next/server";

import { proxyJson } from "@/lib/core-proxy";

export const dynamic = "force-dynamic";

type Context = { params: Promise<{ id: string }> };

export async function POST(request: Request, { params }: Context) {
  const { id } = await params;
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Некорректный запрос" }, { status: 400 });
  }
  return proxyJson(`/api/v1/vacancies/${encodeURIComponent(id)}/fit`, "POST", body);
}
