package io.citadel.core.repository;

import io.citadel.core.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** 检测到刷新令牌重放时，一次性吊销该用户全部活跃令牌。 */
  @Modifying
  @Query(
      "UPDATE RefreshToken rt SET rt.revokedAt = :now "
          + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
  void revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
