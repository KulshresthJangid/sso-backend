package com.sso.repository;

import com.sso.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findByIdAndActiveTrue(UUID id);

    Optional<Workspace> findByOrganizationIdAndSlugAndActiveTrue(UUID orgId, String slug);

    List<Workspace> findAllByOrganizationIdAndActiveTrue(UUID orgId);

    boolean existsByOrganizationIdAndSlug(UUID orgId, String slug);
}
