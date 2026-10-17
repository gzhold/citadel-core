package io.citadel.core.security;

import io.citadel.core.dto.CryptoKeyResponse;
import io.citadel.core.dto.PasswordEnvelope;
import io.citadel.core.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 应用层密码信封加密（RSA-OAEP-SHA256 包裹 AES-256-GCM 会话密钥）。
 *
 * <p><b>安全定位与威胁模型（诚实验证用）</b>：
 *
 * <ul>
 *   <li>公钥经 TLS 分发，因此本机制<b>不能防御能攻破 TLS 的中间人</b>（对方同样可替换公钥）；
 *   <li>传输机密性由 TLS 保证（生产强制 HTTPS，见 SecurityConfig），本机制不替代它；
 *   <li>真实增量收益：密码明文<b>不出现在请求体</b>——防误配置的请求日志、经 TLS 卸载的 WAF/企业代理
 *       拿到的只是密文；这是常见的等保/安全测评检查项（"敏感信息传输加密"）；
 *   <li>私钥仅存于实例内存（每次启动重新生成），服务重启即轮换。
 * </ul>
 *
 * <p>多实例说明：Cloud Run 多实例各有独立密钥对，keyId 不匹配时抛 CRYPTO_KEY_UNKNOWN， 客户端重新获取公钥重试一次即可（幂等设计）。
 */
@Service
public class AuthCryptoService {

  private static final String ALGORITHM = "RSA-OAEP-SHA256+AES-256-GCM";

  private final String keyId = UUID.randomUUID().toString();
  private final KeyPair keyPair;
  private final String publicKeyBase64;
  private final SecureRandom random = new SecureRandom();

  public AuthCryptoService() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048, random);
      this.keyPair = generator.generateKeyPair();
      this.publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    } catch (Exception ex) {
      throw new IllegalStateException("初始化密码信封密钥对失败", ex);
    }
  }

  public CryptoKeyResponse publicKeyResponse() {
    return new CryptoKeyResponse(keyId, ALGORITHM, publicKeyBase64);
  }

  /** 解密密码信封。keyId 不属于本实例 → CRYPTO_KEY_UNKNOWN（客户端应重取公钥重试）；其余失败一律 400。 */
  public String decryptPassword(PasswordEnvelope envelope) {
    if (!keyId.equals(envelope.keyId())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CRYPTO_KEY_UNKNOWN", "加密公钥已轮换，请获取新公钥后重试");
    }
    try {
      var decoder = Base64.getDecoder();
      byte[] wrappedKey = decoder.decode(envelope.encryptedKey());
      byte[] iv = decoder.decode(envelope.iv());
      byte[] ciphertext = decoder.decode(envelope.ciphertext());

      var rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      rsa.init(
          Cipher.DECRYPT_MODE,
          keyPair.getPrivate(),
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
      byte[] aesKeyBytes = rsa.doFinal(wrappedKey);
      if (aesKeyBytes.length != 32) {
        throw new IllegalArgumentException("AES 密钥长度非法");
      }
      SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
      var aes = Cipher.getInstance("AES/GCM/NoPadding");
      aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
      return new String(aes.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_DECRYPT_FAILED", "密码信封解密失败");
    }
  }

  /** 测试支持：按本服务公钥加密（生产代码不得调用）。 */
  public static X509EncodedKeySpec publicKeySpec(CryptoKeyResponse response) {
    return new X509EncodedKeySpec(Base64.getDecoder().decode(response.publicKey()));
  }

  /** 供测试构造 KeyFactory 使用（避免测试代码直接依赖内部字段）。 */
  public static KeyFactory keyFactory() {
    try {
      return KeyFactory.getInstance("RSA");
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
