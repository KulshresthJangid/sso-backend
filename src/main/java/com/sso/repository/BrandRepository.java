package com.sso.repository;

import com.sso.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findBySlug(String slug);
    Optional<Brand> findBySlugAndActiveTrue(String slug);
    boolean existsBySlug(String slug);
}
