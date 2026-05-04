package com.sso.repository;

import com.sso.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findAllByOrganizationId(UUID orgId);
    /** App-specific roles */
    List<Role> findAllByOrganizationIdAndClientId(UUID orgId, String clientId);
    /** Org-level roles (client_id IS NULL) */
    List<Role> findAllByOrganizationIdAndClientIdIsNull(UUID orgId);
}
