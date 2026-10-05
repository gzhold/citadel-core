"use client";

import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LogOut, ShieldCheck } from "lucide-react";
import type { MemberRole } from "@citadel/api-contract";
import { fetchMe, logout } from "@/api/auth";
import { useAuthStore } from "@/store/auth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const ROLE_LABELS: Record<MemberRole, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  MEMBER: "Member",
};

export default function DashboardPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const user = useAuthStore((state) => state.user);
  const clearSession = useAuthStore((state) => state.clearSession);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["me"],
    queryFn: fetchMe,
    retry: 1,
  });

  async function handleLogout() {
    if (refreshToken) {
      await logout(refreshToken);
    }
    clearSession();
    queryClient.clear();
    router.replace("/login");
  }

  return (
    <div className="min-h-screen">
      <header className="border-b bg-background">
        <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-6">
          <div className="flex items-center gap-2 font-semibold tracking-tight">
            <ShieldCheck className="size-5" aria-hidden />
            Citadel
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-muted-foreground">{user?.email}</span>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="size-4" aria-hidden />
              退出
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-6 py-10">
        <h1 className="text-2xl font-bold tracking-tight">
          {data ? `欢迎回来，${data.user.displayName}` : "工作台"}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          这是受保护页面：数据来自 GET /api/v1/me（JWT Bearer 认证）。
        </p>

        <div className="mt-8">
          <Card>
            <CardHeader>
              <CardTitle>我的工作区</CardTitle>
              <CardDescription>注册时自动创建，你是最初的 Owner</CardDescription>
            </CardHeader>
            <CardContent>
              {isLoading ? (
                <p className="text-sm text-muted-foreground">加载中…</p>
              ) : isError ? (
                <p className="text-sm text-destructive">
                  加载失败：{error instanceof Error ? error.message : "未知错误"}
                </p>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="py-2 font-medium">名称</th>
                      <th className="py-2 font-medium">Slug</th>
                      <th className="py-2 font-medium">角色</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data?.memberships.map((membership) => (
                      <tr key={membership.workspaceId} className="border-b last:border-0">
                        <td className="py-2">{membership.name}</td>
                        <td className="py-2 font-mono text-xs text-muted-foreground">
                          {membership.slug}
                        </td>
                        <td className="py-2">
                          <span className="inline-flex items-center rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium">
                            {ROLE_LABELS[membership.role]}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  );
}
