package com.sso.controller;

import com.sso.dto.CreateBrandRequest;
import com.sso.entity.Brand;
import com.sso.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateBrandRequest req) {
        Brand brand = brandService.create(req);
        return Map.of("id", brand.getId(), "name", brand.getName(), "slug", brand.getSlug());
    }

    @GetMapping("/{slug}")
    public Map<String, Object> get(@PathVariable String slug) {
        Brand brand = brandService.getBySlug(slug);
        return Map.of("id", brand.getId(), "name", brand.getName(),
                "slug", brand.getSlug(), "active", brand.isActive());
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
        return config;
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String slug) {
        brandService.deactivate(slug);
    }
}
