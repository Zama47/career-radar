"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { chartAxis, chartColors, chartGridStroke } from "@/lib/chart-theme";
import { formatShortDate } from "@/lib/format";
import type { TimelinePoint } from "@/lib/types";

export function TimelineChart({ points }: { points: TimelinePoint[] }) {
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 8, right: 12, bottom: 0, left: -16 }}>
          <CartesianGrid stroke={chartGridStroke} strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="date"
            tickFormatter={(value: string) => formatShortDate(value)}
            minTickGap={16}
            {...chartAxis}
          />
          <YAxis allowDecimals={false} width={36} {...chartAxis} />
          <Tooltip
            labelFormatter={(label) => formatShortDate(String(label))}
            formatter={(value) => [String(value), "вакансий"]}
            contentStyle={{ borderRadius: 8, fontSize: 12 }}
          />
          <Line
            type="monotone"
            dataKey="count"
            name="Вакансии"
            stroke={chartColors.primary}
            strokeWidth={2}
            dot={{ r: 2 }}
            activeDot={{ r: 4 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
