import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker builds set NEXT_STANDALONE=1; local `next start` stays unaffected.
  output: process.env.NEXT_STANDALONE === "1" ? "standalone" : undefined,
};

export default nextConfig;
