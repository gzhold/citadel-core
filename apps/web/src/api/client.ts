import { env } from "@/lib/env";
import { errorResponseSchema } from "@citadel/api-contract";

/** 统一 API 错误：携带后端错误码，便于 UI 做人性化映射。 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** 会话存取适配器：client 不直接依赖具体状态管理实现。 */
export interface TokenAccessor {
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  onSessionRenewed(accessToken: string, refreshToken: string): void;
  onSessionExpired(): void;
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  /** false 时不附带访问令牌（登录/注册/刷新自身）。 */
  auth?: boolean;
}

async function toApiError(response: Response): Promise<ApiError> {
  let code = `HTTP_${response.status}`;
  let message = `请求失败（${response.status}）`;
  try {
    const raw: unknown = await response.json();
    const parsed = errorResponseSchema.safeParse(raw);
    if (parsed.success) {
      code = parsed.data.code;
      message = parsed.data.message;
    }
  } catch {
    // 非 JSON 响应体，保留默认信息
  }
  return new ApiError(response.status, code, message);
}

/**
 * 轻量 API client（fetch 封装）：
 * - 自动附带 Bearer 令牌；
 * - 401 时用 refresh token 轮换一次并重放原请求（仅一次，防循环）；
 * - 刷新失败统一抛 ApiError(UNAUTHENTICATED) 并通知会话过期。
 */
export function createApiClient(
  tokens: TokenAccessor,
  baseUrl: string = env.NEXT_PUBLIC_API_BASE_URL,
) {
  async function tryRefresh(): Promise<boolean> {
    const refreshToken = tokens.getRefreshToken();
    if (!refreshToken) {
      return false;
    }
    const response = await fetch(`${baseUrl}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
      cache: "no-store",
    });
    if (!response.ok) {
      return false;
    }
    const data = (await response.json()) as { accessToken: string; refreshToken: string };
    tokens.onSessionRenewed(data.accessToken, data.refreshToken);
    return true;
  }

  async function request<T>(
    path: string,
    options: RequestOptions = {},
    allowRefresh = true,
  ): Promise<T> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const token = tokens.getAccessToken();
    if (token && options.auth !== false) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${baseUrl}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      cache: "no-store",
    });

    if (response.status === 401 && allowRefresh) {
      if (await tryRefresh()) {
        return request<T>(path, options, false);
      }
      tokens.onSessionExpired();
      throw new ApiError(401, "UNAUTHENTICATED", "登录状态已失效，请重新登录");
    }

    if (!response.ok) {
      throw await toApiError(response);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  return { request };
}

export type ApiClient = ReturnType<typeof createApiClient>;
