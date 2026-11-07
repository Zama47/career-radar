import { NextResponse } from "next/server";

import { TOKEN_COOKIE } from "@/lib/config";

export const dynamic = "force-dynamic";

/**
 * Clears a stale/expired token cookie and sends the browser to /login. Without this,
 * /login (which redirects when a cookie exists) and protected pages (which redirect on 401)
 * can bounce forever after JWT_SECRET rotation or token expiry.
 */
export async function GET(request: Request) {
  const response = NextResponse.redirect(new URL("/login", request.url));
  response.cookies.set({
    name: TOKEN_COOKIE,
    value: "",
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 0,
  });
  return response;
}
