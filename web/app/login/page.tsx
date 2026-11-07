import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { Radar } from "lucide-react";

import { LoginForm } from "@/components/login-form";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { TOKEN_COOKIE } from "@/lib/config";

export const dynamic = "force-dynamic";

export default async function LoginPage() {
  const token = (await cookies()).get(TOKEN_COOKIE)?.value;
  if (token) {
    redirect("/");
  }

  return (
    <main className="mx-auto flex min-h-[calc(100dvh-3.5rem)] w-full max-w-md flex-col justify-center px-4 py-12">
      <Card>
        <CardHeader className="space-y-2">
          <div className="bg-primary text-primary-foreground flex size-10 items-center justify-center rounded-lg">
            <Radar className="size-5" />
          </div>
          <CardTitle className="text-2xl">Вход в Career Radar</CardTitle>
          <CardDescription>
            Личный инструмент поиска работы. Используйте email и пароль из{" "}
            <code className="font-mono text-xs">infra/.env</code>.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <LoginForm />
        </CardContent>
      </Card>
    </main>
  );
}
