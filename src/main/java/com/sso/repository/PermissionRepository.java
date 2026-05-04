package com.sso.repository;

import com.sso.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrganizationId(UUID orgId);
    boolean existsByNameAndOrganizationId(String name, UUID orgId);
}
