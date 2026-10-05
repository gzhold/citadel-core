"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuthStore } from "@/store/auth";

/**
 * 受保护路由组布局：未登录（本地无 access token）重定向到 /login。
 * 使用 mounted 门闩避免 SSR 首帧与 localStorage 水合不一致导致的闪烁。
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (mounted && !accessToken) {
      router.replace("/login");
    }
  }, [mounted, accessToken, router]);

  if (!mounted || !accessToken) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/30 text-sm text-muted-foreground">
        正在检查登录状态…
      </div>
    );
  }

  return <div className="min-h-screen bg-muted/30">{children}</div>;
}
