package com.sso.controller;

import com.sso.dto.CreateBrandRequest;
import com.sso.dto.UpdateBrandRequest;
import com.sso.entity.Brand;
import com.sso.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /** Platform console's brand table — gated by platformAdminFilterChain (see SecurityConfig). */
    @GetMapping
    public List<Map<String, Object>> list() {
        return brandService.listAll().stream().map(this::toSummary).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateBrandRequest req) {
        Brand brand = brandService.create(req);
        return toSummary(brand);
    }

    @GetMapping("/{slug}")
    public Map<String, Object> get(@PathVariable String slug) {
        return toSummary(brandService.getBySlug(slug));
    }

    @PutMapping("/{slug}")
    public Map<String, Object> update(@PathVariable String slug, @Valid @RequestBody UpdateBrandRequest req) {
        return toSummary(brandService.update(slug, req));
    }

    private Map<String, Object> toSummary(Brand brand) {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("id", brand.getId());
        summary.put("name", brand.getName());
        summary.put("slug", brand.getSlug());
        summary.put("logoUrl", brand.getLogoUrl());
        summary.put("primaryColor", brand.getPrimaryColor());
        summary.put("secondaryColor", brand.getSecondaryColor());
        summary.put("landingTemplate", brand.getLandingTemplate());
        summary.put("dashboardTemplate", brand.getDashboardTemplate());
        summary.put("landingFont", brand.getLandingFont());
        summary.put("dashboardFont", brand.getDashboardFont());
        summary.put("active", brand.isActive());
        return summary;
    }

    /**
     * Public, unauthenticated — bare /kaizex/ (kaizex-frontend) lists every
     * active white-label brand as a card here. Read-only, display fields
     * only (same shape as /config below, plus slug) — no onboarding/edit/
     * delete capability lives behind this; that's still fully gated behind
     * platformAdminFilterChain via /api/brands itself (list() above,
     * requires ROLE_PLATFORM_ADMIN) and only reachable through
     * /sso/platform/brands. See SecurityConfig for both the
     * platformBrandsMatcher exclusion and the permitAll rule this needs.
     */
    @GetMapping("/public")
    public List<Map<String, Object>> listPublic() {
        return brandService.listAll().stream()
                .filter(Brand::isActive)
                .map(this::toPublicSummary)
                .toList();
    }

    private Map<String, Object> toPublicSummary(Brand brand) {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("name", brand.getName());
        summary.put("slug", brand.getSlug());
        summary.put("logoUrl", brand.getLogoUrl());
        summary.put("primaryColor", brand.getPrimaryColor());
        summary.put("secondaryColor", brand.getSecondaryColor());
        return summary;
    }

    /**
     * Public, unauthenticated — the frontend fetches this at runtime (keyed
     * by whatever brand slug is in its own URL) to skin itself. Deliberately
     * returns only visual fields — never client_id/client_secret, which live
     * in registered_clients and are never exposed over HTTP.
     */
    @GetMapping("/{slug}/config")
    public Map<String, Object> getConfig(@PathVariable String slug) {
        Brand brand = brandService.getBySlug(slug);
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("slug", brand.getSlug());
        config.put("name", brand.getName());
        config.put("logoUrl", brand.getLogoUrl());
        config.put("primaryColor", brand.getPrimaryColor());
        config.put("secondaryColor", brand.getSecondaryColor());
        config.put("landingTemplate", brand.getLandingTemplate());
        config.put("dashboardTemplate", brand.getDashboardTemplate());
        config.put("landingFont", brand.getLandingFont());
        config.put("dashboardFont", brand.getDashboardFont());
        return config;
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String slug) {
        brandService.deactivate(slug);
    }
}
