/**
 * Values shared by server and client code. Keep this module free of `next/headers`.
 */
export const DEFAULT_API_URL = "http://localhost:8080";

export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? DEFAULT_API_URL).replace(/\/+$/, "");

export const TOKEN_COOKIE = "cr_token";
