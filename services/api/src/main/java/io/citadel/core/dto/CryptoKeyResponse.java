package io.citadel.core.dto;

/** 登录/注册用的服务端公钥信息。keyId 用于多实例/轮换场景：解密方与密钥生成方不同实例时返回 CRYPTO_KEY_UNKNOWN，客户端应重新取公钥并重试一次。 */
public record CryptoKeyResponse(String keyId, String algorithm, String publicKey) {}
