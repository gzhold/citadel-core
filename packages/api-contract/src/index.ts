import { z } from "zod";

/**
 * 前后端共享 API 契约（单一事实来源）。
 *
 * - 表单校验（react-hook-form + zodResolver）与响应解析共用同一组 schema；
 * - 后端字段变更时在此处同步，TypeScript 会在编译期暴露不一致。
 */

export const memberRoleSchema = z.enum(["OWNER", "ADMIN", "MEMBER"]);
export type MemberRole = z.infer<typeof memberRoleSchema>;

// ---- 请求 ----

export const registerSchema = z.object({
  email: z.email("请输入有效邮箱"),
  password: z.string().min(8, "密码至少 8 位").max(72, "密码最长 72 位"),
  displayName: z.string().min(1, "请输入昵称").max(100, "昵称最长 100 字符"),
});
export type RegisterInput = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email: z.email("请输入有效邮箱"),
  password: z.string().min(1, "请输入密码"),
});
export type LoginInput = z.infer<typeof loginSchema>;

// ---- 响应 ----

export const userSchema = z.object({
  id: z.number(),
  email: z.string(),
  displayName: z.string(),
});
export type UserDto = z.infer<typeof userSchema>;

export const workspaceSchema = z.object({
  id: z.number(),
  name: z.string(),
  slug: z.string(),
  role: memberRoleSchema,
});
export type WorkspaceDto = z.infer<typeof workspaceSchema>;

export const authResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number(),
  user: userSchema,
  workspace: workspaceSchema,
});
export type AuthResponse = z.infer<typeof authResponseSchema>;

export const membershipSchema = z.object({
  workspaceId: z.number(),
  name: z.string(),
  slug: z.string(),
  role: memberRoleSchema,
});
export type MembershipDto = z.infer<typeof membershipSchema>;

export const meResponseSchema = z.object({
  user: userSchema,
  memberships: z.array(membershipSchema),
});
export type MeResponse = z.infer<typeof meResponseSchema>;

export const errorResponseSchema = z.object({
  code: z.string(),
  message: z.string(),
  timestamp: z.string().optional(),
});
export type ErrorResponseDto = z.infer<typeof errorResponseSchema>;
