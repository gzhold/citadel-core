import {
  authResponseSchema,
  loginSchema,
  meResponseSchema,
  registerSchema,
  type AuthResponse,
  type LoginInput,
  type MeResponse,
  type RegisterInput,
} from "@citadel/api-contract";
import { createApiClient } from "@/api/client";
import { useAuthStore } from "@/store/auth";

const tokens = {
  getAccessToken: () => useAuthStore.getState().accessToken,
  getRefreshToken: () => useAuthStore.getState().refreshToken,
  onSessionRenewed: (accessToken: string, refreshToken: string) =>
    useAuthStore.getState().renewSession(accessToken, refreshToken),
  onSessionExpired: () => useAuthStore.getState().clearSession(),
};

const client = createApiClient(tokens);

/** 响应统一过一遍契约 schema：后端字段漂移在第一时间暴露。 */
export async function login(input: LoginInput): Promise<AuthResponse> {
  const raw = await client.request<unknown>("/api/v1/auth/login", {
    method: "POST",
    body: loginSchema.parse(input),
    auth: false,
  });
  return authResponseSchema.parse(raw);
}

export async function register(input: RegisterInput): Promise<AuthResponse> {
  const raw = await client.request<unknown>("/api/v1/auth/register", {
    method: "POST",
    body: registerSchema.parse(input),
    auth: false,
  });
  return authResponseSchema.parse(raw);
}

export async function logout(refreshToken: string): Promise<void> {
  try {
    await client.request<void>("/api/v1/auth/logout", {
      method: "POST",
      body: { refreshToken },
      auth: false,
    });
  } catch {
    // 尽力而为：本地会话总是会被清除
  }
}

export async function fetchMe(): Promise<MeResponse> {
  const raw = await client.request<unknown>("/api/v1/me");
  return meResponseSchema.parse(raw);
}
