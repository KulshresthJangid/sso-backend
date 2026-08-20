package com.sso.repository;

import com.sso.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findBySlug(String slug);
    Optional<Brand> findBySlugAndActiveTrue(String slug);
    boolean existsBySlug(String slug);

    /** For the platform console's brand list — includes inactive brands, admin should see the full picture. */
    List<Brand> findAllByOrderByNameAsc();
}
