package io.citadel.core.repository;

import io.citadel.core.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  /** 邮箱统一小写存储，查询侧同样忽略大小写以兜底。 */
  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);
}
