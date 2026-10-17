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

/**
 * 密码信封（应用层加密，防御纵深）：AES-256-GCM 会话密钥经服务端 RSA-OAEP(SHA-256)
 * 公钥包裹（encryptedKey），密码明文经会话密钥加密（ciphertext + iv）。全 Base64。
 * 公钥来源：GET /api/v1/auth/crypto/key。TLS 仍是传输安全基础，本机制不替代它。
 */
export const passwordEnvelopeSchema = z.object({
  keyId: z.string(),
  encryptedKey: z.string(),
  iv: z.string(),
  ciphertext: z.string(),
});
export type PasswordEnvelope = z.infer<typeof passwordEnvelopeSchema>;

/** 线上请求体：表单收集明文（loginSchema/registerSchema 做 UX 校验），发送前经信封加密。 */
export const loginRequestSchema = z.object({
  email: z.email(),
  password: passwordEnvelopeSchema,
});
export const registerRequestSchema = z.object({
  email: z.email(),
  password: passwordEnvelopeSchema,
  displayName: z.string().min(1).max(100),
});

export const cryptoKeyResponseSchema = z.object({
  keyId: z.string(),
  algorithm: z.string(),
  publicKey: z.string(),
});
export type CryptoKeyResponse = z.infer<typeof cryptoKeyResponseSchema>;

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
