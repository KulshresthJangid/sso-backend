package com.sso.controller;

import com.sso.dto.CreateSuperAdminRequest;
import com.sso.entity.Brand;
import com.sso.entity.User;
import com.sso.repository.UserRepository;
import com.sso.service.BrandService;
import com.sso.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * The one SUPER_ADMIN account per brand — created from the onboarding
 * wizard's final step (see sso/frontend BrandsPage.tsx). Lives under
 * /api/brands/** so it's covered by SecurityConfig's platformAdminFilterChain
 * exactly like brand/client management — only the platform operator creates
 * these, never self-service.
 */
@RestController
@RequestMapping("/api/brands/{slug}/super-admin")
@RequiredArgsConstructor
public class BrandSuperAdminController {

    private final UserService userService;
    private final BrandService brandService;
    private final UserRepository userRepo;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable String slug, @Valid @RequestBody CreateSuperAdminRequest req) {
        User admin = userService.createSuperAdmin(slug, req);
        return Map.of("id", admin.getId(), "email", admin.getEmail());
    }

    /** Lets the wizard/brands table show whether a brand already has one, without exposing the password hash. */
    @GetMapping
    public Map<String, Object> get(@PathVariable String slug) {
        Brand brand = brandService.getBySlug(slug);
        return userRepo.findFirstByBrand_IdAndOrgRoleAndActiveTrue(brand.getId(), User.OrgRole.SUPER_ADMIN)
                .<Map<String, Object>>map(u -> Map.of("exists", true, "email", u.getEmail()))
                .orElseGet(() -> Map.of("exists", false));
    }
}
