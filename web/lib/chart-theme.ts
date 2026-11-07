/**
 * Fixed palette for Recharts. The app has no theme switcher yet, so plain hex values render
 * the same in light and dark mode and avoid CSS-variable quirks in SVG attributes.
 */
export const chartColors = {
  primary: "#2563eb",
  teal: "#0d9488",
  amber: "#d97706",
  violet: "#7c3aed",
  slate: "#64748b",
};

export const chartAxis = {
  tick: { fontSize: 12, fill: "#6b7280" },
  axisLine: false,
  tickLine: false,
} as const;

export const chartGridStroke = "#e5e7eb";
