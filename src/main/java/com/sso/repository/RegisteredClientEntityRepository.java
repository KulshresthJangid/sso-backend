package com.sso.repository;

import com.sso.entity.RegisteredClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegisteredClientEntityRepository extends JpaRepository<RegisteredClientEntity, String> {
    Optional<RegisteredClientEntity> findByClientId(String clientId);
    Optional<RegisteredClientEntity> findByClientIdAndOrganizationId(String clientId, UUID orgId);
    List<RegisteredClientEntity> findAllByOrganizationId(UUID orgId);
}
