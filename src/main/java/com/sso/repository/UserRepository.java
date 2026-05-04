package com.sso.repository;

import com.sso.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndOrganizationId(String email, UUID orgId);
    Optional<User> findByEmailAndOrganizationIdAndActiveTrue(String email, UUID orgId);
    List<User> findAllByOrganizationId(UUID orgId);
    boolean existsByEmailAndOrganizationId(String email, UUID orgId);
}
