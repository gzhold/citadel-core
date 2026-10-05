package io.citadel.core.repository;

import io.citadel.core.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

  boolean existsBySlug(String slug);
}
