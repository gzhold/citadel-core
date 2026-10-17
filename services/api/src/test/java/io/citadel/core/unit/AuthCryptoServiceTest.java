package io.citadel.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.citadel.core.dto.CryptoKeyResponse;
import io.citadel.core.exception.ApiException;
import io.citadel.core.security.AuthCryptoService;
import io.citadel.core.testsupport.ClientCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 密码信封加密服务：与测试侧 ClientCrypto（等价于前端 WebCrypto）互操作。 */
class AuthCryptoServiceTest {

  private final AuthCryptoService service = new AuthCryptoService();

  @Test
  @DisplayName("往返：公钥加密 → 服务端解密还原明文")
  void roundtrip() {
    var key = service.publicKeyResponse();

    String plaintext = service.decryptPassword(ClientCrypto.encrypt(key, "password123!中文"));

    assertThat(plaintext).isEqualTo("password123!中文");
  }

  @Test
  @DisplayName("keyId 不属于本实例 → CRYPTO_KEY_UNKNOWN（客户端应重取公钥重试）")
  void unknownKeyIdRejected() {
    CryptoKeyResponse key = service.publicKeyResponse();
    var foreign = new CryptoKeyResponse("not-this-instance", key.algorithm(), key.publicKey());

    assertThatThrownBy(() -> service.decryptPassword(ClientCrypto.encrypt(foreign, "x")))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo("CRYPTO_KEY_UNKNOWN"));
  }

  @Test
  @DisplayName("密文被篡改 → PASSWORD_DECRYPT_FAILED（GCM 完整性校验）")
  void tamperedCiphertextRejected() {
    var key = service.publicKeyResponse();
    var envelope = ClientCrypto.encrypt(key, "password123");
    var tampered =
        new io.citadel.core.dto.PasswordEnvelope(
            envelope.keyId(),
            envelope.encryptedKey(),
            envelope.iv(),
            java.util.Base64.getEncoder()
                .encodeToString(
                    new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18}));

    assertThatThrownBy(() -> service.decryptPassword(tampered))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).code()).isEqualTo("PASSWORD_DECRYPT_FAILED"));
  }
}
