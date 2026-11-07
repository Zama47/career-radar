import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: "Career Radar",
  description: "Personal job-search operating system: vacancies, market analytics, resumes, applications.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-dvh antialiased">{children}</body>
    </html>
  );
}
