import { proxyFile } from "@/lib/core-proxy";

export const dynamic = "force-dynamic";

type Context = { params: Promise<{ id: string }> };

export async function GET(_request: Request, { params }: Context) {
  const { id } = await params;
  return proxyFile(`/api/v1/resumes/${encodeURIComponent(id)}/file`);
}
