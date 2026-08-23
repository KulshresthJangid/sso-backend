package com.sso.controller;

import com.sso.entity.User;
import com.sso.exception.SSOException;
import com.sso.repository.UserRepository;
import com.sso.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Session-cookie "who am I" — the sso-frontend login flow needs this to
 * work identically for an ORG_ADMIN (has an org) and a brand-level
 * SUPER_ADMIN (doesn't). The old check (GET /api/orgs/{slug}/users
 * succeeding) only ever worked for the former.
 *
 * Deliberately NOT under /api/me/** — that path is already claimed by
 * SecurityConfig's resourceServerFilterChain (JWT-bearer only, stateless),
 * which would shadow a session-cookie version of the same path entirely.
 * Called brand-prefixed (/{slug}/api/session/me) so TenantResolutionFilter
 * populates TenantContext — this endpoint is excluded from tenant
 * resolution otherwise (every bare /api/** path is, see
 * TenantResolutionFilter.NON_TENANT_PREFIXES).
 */
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final UserRepository userRepo;

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        var brand = TenantContext.get();
        if (brand == null) {
            throw SSOException.badRequest("No brand in URL — call this as /{brandSlug}/api/session/me");
        }

        String email = authentication.getName();
        User user = userRepo.findFirstByEmailAndOrganization_Brand_IdAndActiveTrue(email, brand.getId())
                .or(() -> userRepo.findFirstByEmailAndBrand_IdAndOrgRoleAndActiveTrue(email, brand.getId(), User.OrgRole.SUPER_ADMIN))
                .orElseThrow(() -> SSOException.notFound("User not found in this brand"));

        Map<String, Object> result = new HashMap<>();
        result.put("email", user.getEmail());
        result.put("orgRole", user.getOrgRole().name());
        result.put("brandSlug", brand.getSlug());
        result.put("brandName", brand.getName());
        if (user.getOrganization() != null) {
            result.put("orgSlug", user.getOrganization().getSlug());
            result.put("orgName", user.getOrganization().getName());
        }
        return result;
    }
}
