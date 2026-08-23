package com.sso.service;

import com.sso.dto.CreateBrandRequest;
import com.sso.dto.UpdateBrandRequest;
import com.sso.entity.Brand;
import com.sso.exception.SSOException;
import com.sso.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepo;

    public List<Brand> listAll() {
        return brandRepo.findAllByOrderByNameAsc();
    }

    @Transactional
    public Brand create(CreateBrandRequest req) {
        if (brandRepo.existsBySlug(req.slug())) {
            throw SSOException.conflict("Slug already taken: " + req.slug());
        }
        return brandRepo.save(Brand.builder()
                .name(req.name())
                .slug(req.slug())
                .logoUrl(req.logoUrl())
                .primaryColor(req.primaryColor())
                .secondaryColor(req.secondaryColor())
                .landingTemplate(req.landingTemplate() != null ? req.landingTemplate() : "MINIMAL")
                .dashboardTemplate(req.dashboardTemplate() != null ? req.dashboardTemplate() : "MINIMAL")
                .landingFont(req.landingFont())
                .dashboardFont(req.dashboardFont())
                .build());
    }

    public Brand getBySlug(String slug) {
        return brandRepo.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> SSOException.notFound("Brand not found: " + slug));
    }

    /** Everything but the slug is editable post-onboarding — see UpdateBrandRequest for why. */
    @Transactional
    public Brand update(String slug, UpdateBrandRequest req) {
        Brand brand = getBySlug(slug);
        brand.setName(req.name());
        brand.setLogoUrl(req.logoUrl());
        brand.setPrimaryColor(req.primaryColor());
        brand.setSecondaryColor(req.secondaryColor());
        brand.setLandingTemplate(req.landingTemplate() != null ? req.landingTemplate() : "MINIMAL");
        brand.setDashboardTemplate(req.dashboardTemplate() != null ? req.dashboardTemplate() : "MINIMAL");
        brand.setLandingFont(req.landingFont());
        brand.setDashboardFont(req.dashboardFont());
        return brandRepo.save(brand);
    }

    @Transactional
    public void deactivate(String slug) {
        Brand brand = getBySlug(slug);
        brand.setActive(false);
        brandRepo.save(brand);
    }
}
