import type { ReactNode } from "react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * Shared shell for every chart: title, units/period caption, optional insight and a uniform
 * empty state.
 */
export function ChartFrame({
  title,
  description,
  badge,
  insight,
  empty = false,
  emptyMessage = "Нет данных за период",
  className,
  children,
}: {
  title: string;
  description: string;
  badge?: string;
  insight?: ReactNode;
  empty?: boolean;
  emptyMessage?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <Card className={cn("flex flex-col", className)}>
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1">
            <CardTitle className="text-base">{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          {badge && (
            <span className="text-muted-foreground shrink-0 text-xs whitespace-nowrap">{badge}</span>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col justify-between gap-3">
        {empty ? (
          <p className="text-muted-foreground flex h-40 items-center justify-center rounded-lg border border-dashed text-sm">
            {emptyMessage}
          </p>
        ) : (
          children
        )}
        {!empty && insight && (
          <p className="text-muted-foreground border-t pt-3 text-xs leading-relaxed">{insight}</p>
        )}
      </CardContent>
    </Card>
  );
}
