import {
  authResponseSchema,
  loginSchema,
  meResponseSchema,
  registerSchema,
  type AuthResponse,
  type MeResponse,
} from "@citadel/api-contract";
import { ApiError, createApiClient } from "@/api/client";
import { encryptPassword, forgetKey } from "@/lib/crypto";
import { useAuthStore } from "@/store/auth";

const tokens = {
  getAccessToken: () => useAuthStore.getState().accessToken,
  getRefreshToken: () => useAuthStore.getState().refreshToken,
  onSessionRenewed: (accessToken: string, refreshToken: string) =>
    useAuthStore.getState().renewSession(accessToken, refreshToken),
  onSessionExpired: () => useAuthStore.getState().clearSession(),
};

const client = createApiClient(tokens);

/**
 * 发送带密码信封的请求：请求体中不出现明文密码（应用层加密，安全定位见 lib/crypto.ts）。
 * CRYPTO_KEY_UNKNOWN（服务端密钥轮换 / 多实例 keyId 不匹配）时重取公钥并重试一次。
 */
async function postWithEncryptedPassword<T>(
  path: string,
  payload: { email: string; password: string } & Record<string, unknown>,
): Promise<T> {
  const send = async () => {
    const { password, ...rest } = payload;
    return client.request<T>(path, {
      method: "POST",
      body: { ...rest, password: await encryptPassword(password) },
      auth: false,
    });
  };
  try {
    return await send();
  } catch (error) {
    if (error instanceof ApiError && error.code === "CRYPTO_KEY_UNKNOWN") {
      forgetKey();
      return send();
    }
    throw error;
  }
}

/** 响应统一过一遍契约 schema：后端字段漂移在第一时间暴露。 */
export async function login(input: { email: string; password: string }): Promise<AuthResponse> {
  loginSchema.parse(input); // 发送前做表单级校验（与 UI 一致的失败语义）
  const raw = await postWithEncryptedPassword<unknown>("/api/v1/auth/login", {
    email: input.email,
    password: input.password,
  });
  return authResponseSchema.parse(raw);
}

export async function register(input: {
  email: string;
  password: string;
  displayName: string;
}): Promise<AuthResponse> {
  registerSchema.parse(input);
  const raw = await postWithEncryptedPassword<unknown>("/api/v1/auth/register", input);
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
