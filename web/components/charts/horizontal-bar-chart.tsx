"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { chartAxis, chartColors, chartGridStroke } from "@/lib/chart-theme";
import { formatCompact } from "@/lib/format";

export type BarDatum = {
  label: string;
  value: number;
};

/**
 * Horizontal bar chart used for top skills and top professions. A horizontal layout keeps
 * long Russian occupation names readable without rotated ticks.
 */
export function HorizontalBarChart({
  data,
  color = chartColors.teal,
  valueLabel = "вакансий",
  height,
}: {
  data: BarDatum[];
  color?: string;
  valueLabel?: string;
  height?: number;
}) {
  const chartHeight = height ?? Math.max(160, data.length * 32 + 32);

  return (
    <div style={{ height: chartHeight }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 4, right: 16, bottom: 0, left: 0 }}
        >
          <CartesianGrid stroke={chartGridStroke} strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" allowDecimals={false} {...chartAxis} />
          <YAxis type="category" dataKey="label" width={124} {...chartAxis} />
          <Tooltip
            cursor={{ fill: "rgba(37, 99, 235, 0.06)" }}
            formatter={(value) => [formatCompact(Number(value)), valueLabel]}
            contentStyle={{ borderRadius: 8, fontSize: 12 }}
          />
          <Bar dataKey="value" fill={color} radius={[0, 4, 4, 0]} barSize={16} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
