import { NextResponse } from "next/server";

/** 容器健康检查端点（docker-compose / Cloud Run 探针共用）。 */
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ status: "ok" });
}
