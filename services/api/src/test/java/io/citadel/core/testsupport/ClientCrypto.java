package io.citadel.core.testsupport;

import io.citadel.core.dto.CryptoKeyResponse;
import io.citadel.core.dto.PasswordEnvelope;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/** 测试侧密码信封加密：与前端 WebCrypto（RSA-OAEP-SHA256 包裹 AES-256-GCM）逐位对等。 供单元/集成测试构造合法信封。 */
public final class ClientCrypto {

  private static final SecureRandom RANDOM = new SecureRandom();

  private ClientCrypto() {}

  public static PasswordEnvelope encrypt(CryptoKeyResponse key, String plaintext) {
    try {
      var publicKey =
          java.security.KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey())));

      byte[] aesKey = new byte[32];
      RANDOM.nextBytes(aesKey);
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);

      var rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      rsa.init(
          Cipher.ENCRYPT_MODE,
          publicKey,
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
      byte[] wrappedKey = rsa.doFinal(aesKey);

      var aes = Cipher.getInstance("AES/GCM/NoPadding");
      aes.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
      byte[] ciphertext = aes.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      var encoder = Base64.getEncoder();
      return new PasswordEnvelope(
          key.keyId(),
          encoder.encodeToString(wrappedKey),
          encoder.encodeToString(iv),
          encoder.encodeToString(ciphertext));
    } catch (Exception ex) {
      throw new IllegalStateException("测试信封加密失败", ex);
    }
  }
}
