import type { Metadata } from "next";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "Citadel — 团队协作平台",
    template: "%s | Citadel",
  },
  description:
    "B2B 多租户团队协作平台：看板、工单与组织治理（租户隔离 / RBAC / 审计 / 订阅计费）。个人作品集项目。",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
