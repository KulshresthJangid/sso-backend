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

    /**
     * Brand-scoped login lookup — the org isn't known yet at the point a
     * brand-level login form is submitted (only the brand, from the URL),
     * so this searches every org under the brand for a matching active user.
     * Assumes email is unique per-brand across its orgs; if the same email
     * exists in two orgs under one brand this returns whichever JPA/the DB
     * happens to pick first — a known limitation, not resolved here.
     */
    Optional<User> findFirstByEmailAndOrganization_Brand_IdAndActiveTrue(String email, UUID brandId);
}
