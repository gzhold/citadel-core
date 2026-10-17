package io.citadel.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 密码信封（应用层加密，防御纵深）。
 *
 * <p>结构：AES-256-GCM 会话密钥经服务端 RSA-OAEP(SHA-256) 公钥包裹（encryptedKey）， 密码明文经该会话密钥 AES-GCM 加密（ciphertext
 * + iv）。所有字段为 Base64。公钥通过 GET /api/v1/auth/crypto/key 获取。
 *
 * <p>安全定位（重要）：TLS 仍是传输安全的基础，本机制不替代 HTTPS；其增量收益是 密码明文不出现在任何请求体日志、WAF 或 TLS 卸载代理中。详见
 * AuthCryptoService 的说明。
 */
public record PasswordEnvelope(
    @NotBlank String keyId,
    @NotBlank String encryptedKey,
    @NotBlank String iv,
    @NotBlank String ciphertext) {}
