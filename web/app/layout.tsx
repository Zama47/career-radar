import type { Metadata } from "next";

import { SiteHeader } from "@/components/site-header";

import "./globals.css";

export const metadata: Metadata = {
  title: "Career Radar",
  description:
    "Personal job-search operating system: vacancies, market analytics, resumes, applications.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ru">
      <body className="bg-background text-foreground min-h-dvh antialiased">
        <SiteHeader />
        {children}
      </body>
    </html>
  );
}
