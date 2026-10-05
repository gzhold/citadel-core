import { z } from "zod";

/**
 * 环境变量校验。
 *
 * - 缺失时回退到本地开发默认值（保证 `next build` 在无环境的 CI 中可预渲染）；
 * - 非法值（非 URL）仍然 fail-fast；
 * - 生产覆盖路径：Vercel 项目环境变量 / docker-compose 的 build arg（NEXT_PUBLIC_* 在构建期内联，
 *   运行期改环境变量不生效）。
 */
const url = z
  .url("NEXT_PUBLIC_API_BASE_URL 必须是合法 URL")
  .parse(process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080");

/**
 * 传输安全：生产构建只允许 HTTPS 的 API 地址（本机 loopback 豁免，供 `next start` 本地预览）。
 * 构建期 fail-fast，防止密码等敏感字段被发往明文 HTTP 通道。
 */
if (
  process.env.NODE_ENV === "production" &&
  url.startsWith("http://") &&
  !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?/.test(url)
) {
  throw new Error(
    `生产环境 NEXT_PUBLIC_API_BASE_URL 必须是 HTTPS（当前：${url}）。` +
      "密码等敏感字段不允许经明文 HTTP 传输。",
  );
}

export const env = { NEXT_PUBLIC_API_BASE_URL: url };
