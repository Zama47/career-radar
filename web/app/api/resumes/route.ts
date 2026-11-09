import { NextResponse } from "next/server";

import { proxyJson } from "@/lib/core-proxy";

export const dynamic = "force-dynamic";

export async function GET() {
  return proxyJson("/api/v1/resumes", "GET");
}

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Некорректный запрос" }, { status: 400 });
  }
  return proxyJson("/api/v1/resumes", "POST", body);
}
