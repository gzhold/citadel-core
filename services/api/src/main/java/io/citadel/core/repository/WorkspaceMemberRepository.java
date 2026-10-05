package io.citadel.core.repository;

import io.citadel.core.entity.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

  List<WorkspaceMember> findByUserIdOrderByJoinedAtAsc(Long userId);

  /** 默认工作区 = 最早加入的成员关系。 */
  Optional<WorkspaceMember> findFirstByUserIdOrderByJoinedAtAsc(Long userId);
}
