import Link from "next/link";
import { ShieldCheck, Users, ScrollText, CreditCard } from "lucide-react";

const PILLARS = [
  {
    icon: ShieldCheck,
    title: "跨租户越权防护",
    description: "请求级租户上下文 + 仓储层强制过滤，A 租户永远拿不到 B 租户的数据。",
  },
  {
    icon: Users,
    title: "RBAC 权限模型",
    description: "Owner / Admin / Member 三级角色，邀请加入流程与权限变更全程审计。",
  },
  {
    icon: ScrollText,
    title: "审计日志",
    description: "append-only 事件表，登录、邀请、权限变更全链路可追溯。",
  },
  {
    icon: CreditCard,
    title: "订阅计费钩子",
    description: "seats 用量计量与 Stripe 订阅状态同步（规划中）。",
  },
] as const;

export default function HomePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b">
        <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-6">
          <div className="flex items-center gap-2 text-lg font-semibold tracking-tight">
            <ShieldCheck className="size-5" aria-hidden />
            Citadel
          </div>
          <nav className="flex items-center gap-2">
            <Link
              href="/login"
              className="inline-flex h-9 items-center justify-center rounded-md px-3 text-sm font-medium hover:bg-accent hover:text-accent-foreground"
            >
              登录
            </Link>
            <Link
              href="/register"
              className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
            >
              免费注册
            </Link>
          </nav>
        </div>
      </header>

      <main className="flex-1">
        <section className="mx-auto w-full max-w-5xl px-6 py-20 text-center">
          <p className="mb-3 text-sm font-medium text-muted-foreground">
            B2B 多租户 · 团队协作 · 组织治理
          </p>
          <h1 className="mx-auto max-w-2xl text-balance text-4xl font-bold tracking-tight sm:text-5xl">
            为团队而建的协作堡垒
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-balance text-muted-foreground">
            看板、工单与数据看板之上，是一整套生产级的组织治理能力：租户隔离、角色权限、审计日志与订阅计费。
          </p>
          <div className="mt-8 flex items-center justify-center gap-3">
            <Link
              href="/register"
              className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-8 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
            >
              创建工作区
            </Link>
            <Link
              href="/login"
              className="inline-flex h-10 items-center justify-center rounded-md border border-input px-8 text-sm font-medium hover:bg-accent"
            >
              登录体验
            </Link>
          </div>
        </section>

        <section className="border-t bg-muted/30">
          <div className="mx-auto grid w-full max-w-5xl gap-4 px-6 py-16 sm:grid-cols-2">
            {PILLARS.map(({ icon: Icon, title, description }) => (
              <div key={title} className="rounded-lg border bg-card p-6 text-left shadow-sm">
                <Icon className="mb-3 size-5 text-muted-foreground" aria-hidden />
                <h2 className="font-semibold">{title}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="border-t py-6 text-center text-xs text-muted-foreground">
        Citadel — 个人作品集项目，用于展示全栈工程能力，与任何同名公司或组织无关。
      </footer>
    </div>
  );
}
