import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 产出自包含 server.js，供 Dockerfile 运行阶段使用
  output: "standalone",
  // monorepo 内 TS 源码包直接编译
  transpilePackages: ["@citadel/api-contract"],
};

export default nextConfig;
