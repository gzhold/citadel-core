import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { AuthResponse, UserDto, WorkspaceDto } from "@citadel/api-contract";

/**
 * 会话状态（zustand + localStorage 持久化）。
 * SSR 阶段 localStorage 不可用，storage 返回 undefined 时 persist 自动跳过。
 */
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserDto | null;
  workspace: WorkspaceDto | null;
  setSession: (response: AuthResponse) => void;
  renewSession: (accessToken: string, refreshToken: string) => void;
  clearSession: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      workspace: null,
      setSession: (response) =>
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          user: response.user,
          workspace: response.workspace,
        }),
      renewSession: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      clearSession: () =>
        set({ accessToken: null, refreshToken: null, user: null, workspace: null }),
    }),
    {
      name: "citadel-auth",
      storage: createJSONStorage(() =>
        typeof window === "undefined"
          ? {
              // SSR 阶段使用 no-op 存储，避免触碰 localStorage（水合由客户端 mounted 门闩兜底）
              getItem: () => null,
              setItem: () => undefined,
              removeItem: () => undefined,
            }
          : window.localStorage,
      ),
    },
  ),
);
