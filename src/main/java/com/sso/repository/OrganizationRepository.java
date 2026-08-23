package com.sso.repository;

import com.sso.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlug(String slug);
    Optional<Organization> findBySlugAndActiveTrue(String slug);
    boolean existsBySlug(String slug);

    /** Every org under one brand — see OrganizationService.listByBrand (Brand Console). */
    List<Organization> findAllByBrand_Id(UUID brandId);
}
