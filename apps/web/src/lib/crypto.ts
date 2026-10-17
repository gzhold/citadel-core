import type { CryptoKeyResponse, PasswordEnvelope } from "@citadel/api-contract";
import { cryptoKeyResponseSchema } from "@citadel/api-contract";
import { env } from "@/lib/env";

/**
 * 密码信封加密（WebCrypto，与服务端 AuthCryptoService 对应）：
 * RSA-OAEP(SHA-256) 包裹随机 AES-256-GCM 会话密钥，会话密钥加密密码明文。
 *
 * 安全定位：TLS 仍是传输安全基础；本机制的增量收益是密码明文不出现在请求体
 * （防请求日志 / TLS 卸载的 WAF / 企业代理拿到明文）。详见服务端注释。
 */

const encoder = new TextEncoder();

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

let cachedKey: { info: CryptoKeyResponse; cryptoKey: CryptoKey } | null = null;

/** 获取服务端公钥（带缓存；keyId 轮换后由调用方调用 forgetKey 重取）。 */
async function fetchKey(): Promise<{ info: CryptoKeyResponse; cryptoKey: CryptoKey }> {
  if (cachedKey) return cachedKey;
  const response = await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/v1/auth/crypto/key`, {
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`获取加密公钥失败（HTTP ${response.status}）`);
  }
  const info = cryptoKeyResponseSchema.parse(await response.json());
  const cryptoKey = await crypto.subtle.importKey(
    "spki",
    Uint8Array.from(atob(info.publicKey), (c) => c.charCodeAt(0)),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"],
  );
  cachedKey = { info, cryptoKey };
  return cachedKey;
}

export function forgetKey(): void {
  cachedKey = null;
}

/** 把明文密码封装为信封。请求体中不再出现明文密码。 */
export async function encryptPassword(plaintext: string): Promise<PasswordEnvelope> {
  const { info, cryptoKey } = await fetchKey();

  const aesKeyBytes = crypto.getRandomValues(new Uint8Array(32));
  const aesKey = await crypto.subtle.importKey("raw", aesKeyBytes, "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));

  const encryptedKey = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, cryptoKey, aesKeyBytes);
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    aesKey,
    encoder.encode(plaintext),
  );

  return {
    keyId: info.keyId,
    encryptedKey: toBase64(new Uint8Array(encryptedKey)),
    iv: toBase64(iv),
    ciphertext: toBase64(new Uint8Array(ciphertext)),
  };
}
