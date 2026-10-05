import { z } from "zod";

/**
 * 环境变量校验。
 *
 * - 缺失时回退到本地开发默认值（保证 `next build` 在无环境的 CI 中可预渲染）；
 * - 非法值（非 URL）仍然 fail-fast；
 * - 生产覆盖路径：Vercel 项目环境变量 / docker-compose 的 build arg（NEXT_PUBLIC_* 在构建期内联，
 *   运行期改环境变量不生效）。
 */
const envSchema = z.object({
  NEXT_PUBLIC_API_BASE_URL: z
    .url("NEXT_PUBLIC_API_BASE_URL 必须是合法 URL")
    .default("http://localhost:8080"),
});

export const env = envSchema.parse({
  NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
});
