import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApiClient, type TokenAccessor } from "./client";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function makeTokens(overrides: Partial<TokenAccessor> = {}): TokenAccessor {
  let accessToken = "access-token";
  const onSessionRenewed = vi.fn((next: string) => {
    accessToken = next;
  });
  return {
    getAccessToken: () => accessToken,
    getRefreshToken: () => "refresh-token",
    onSessionRenewed,
    onSessionExpired: vi.fn(),
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("createApiClient", () => {
  it("请求附带 Bearer 令牌并返回解析后的 JSON", async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ user: { id: 1 }, memberships: [] }));
    vi.stubGlobal("fetch", fetchMock);
    const client = createApiClient(makeTokens());

    const data = await client.request<{ user: { id: number } }>("/api/v1/me");

    expect(data.user.id).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://api.test/api/v1/me",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer access-token" }),
      }),
    );
  });

  it("auth: false 的请求不附带令牌", async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    const client = createApiClient(makeTokens());

    await client.request("/api/v1/auth/login", { method: "POST", body: {}, auth: false });

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("错误响应映射为 ApiError（code + message）", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse({ code: "EMAIL_ALREADY_USED", message: "该邮箱已被注册" }, 409),
      ),
    );
    const client = createApiClient(makeTokens());

    await expect(
      client.request("/api/v1/auth/register", { method: "POST", body: {} }),
    ).rejects.toMatchObject(new ApiError(409, "EMAIL_ALREADY_USED", "该邮箱已被注册"));
  });

  it("401 时自动刷新令牌并重放原请求（仅一次）", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: "UNAUTHENTICATED", message: "expired" }, 401))
      .mockResolvedValueOnce(jsonResponse({ accessToken: "new-at", refreshToken: "new-rt" }))
      .mockResolvedValueOnce(jsonResponse({ user: { id: 7 }, memberships: [] }));
    vi.stubGlobal("fetch", fetchMock);
    const tokens = makeTokens();
    const client = createApiClient(tokens);

    const data = await client.request<{ user: { id: number } }>("/api/v1/me");

    expect(data.user.id).toBe(7);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(tokens.onSessionRenewed).toHaveBeenCalledWith("new-at", "new-rt");
    // 重放后的请求携带新令牌
    const [, retryInit] = fetchMock.mock.calls[2] as unknown as [string, RequestInit];
    expect((retryInit.headers as Record<string, string>).Authorization).toBe("Bearer new-at");
  });

  it("刷新失败时清除会话并抛出 UNAUTHENTICATED", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: "UNAUTHENTICATED", message: "expired" }, 401))
      .mockResolvedValueOnce(
        jsonResponse({ code: "REFRESH_TOKEN_REVOKED", message: "revoked" }, 401),
      );
    vi.stubGlobal("fetch", fetchMock);
    const tokens = makeTokens();
    const client = createApiClient(tokens);

    await expect(client.request("/api/v1/me")).rejects.toMatchObject({
      code: "UNAUTHENTICATED",
    });
    expect(tokens.onSessionExpired).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
