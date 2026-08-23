package com.sso.controller;

import com.sso.dto.ClientResponse;
import com.sso.dto.CreateClientRequest;
import com.sso.dto.CreateOrgRequest;
import com.sso.dto.CreateUserRequest;
import com.sso.entity.Brand;
import com.sso.entity.Organization;
import com.sso.entity.User;
import com.sso.exception.SSOException;
import com.sso.repository.UserRepository;
import com.sso.service.ClientService;
import com.sso.service.OrganizationService;
import com.sso.service.UserService;
import com.sso.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The brand's own SUPER_ADMIN console — self-service org/client management
 * scoped to exactly one brand, called brand-prefixed
 * (/{slug}/api/brand-console/**) so TenantResolutionFilter populates
 * TenantContext. Distinct from /api/brands/** (platform-admin-only, HTTP
 * Basic, manages *all* brands) — this is session-cookie auth (the same
 * login a brand's own users use) restricted to whichever ONE brand the
 * URL slug + the caller's own SUPER_ADMIN record agree on.
 *
 * requireSuperAdmin() is the actual security boundary beyond "authenticated
 * with ROLE_SUPER_ADMIN somewhere" (which SecurityConfig's
 * defaultFilterChain already enforces at the request-matcher level) — a
 * session authenticated as brand X's super admin could otherwise hit
 * /{brandY}/api/brand-console/** and reach brand Y's data, since
 * authentication itself doesn't encode which brand it was established
 * under. This re-checks the specific (email, resolved brand) pair on every
 * call.
 */
@RestController
@RequestMapping("/api/brand-console")
@RequiredArgsConstructor
public class BrandConsoleController {

    private final UserRepository userRepo;
    private final OrganizationService orgService;
    private final UserService userService;
    private final ClientService clientService;

    private Brand requireSuperAdmin(Authentication authentication) {
        Brand brand = TenantContext.get();
        if (brand == null) {
            throw SSOException.badRequest("No brand in URL — call this as /{brandSlug}/api/brand-console/**");
        }
        userRepo.findFirstByEmailAndBrand_IdAndOrgRoleAndActiveTrue(authentication.getName(), brand.getId(), User.OrgRole.SUPER_ADMIN)
                .orElseThrow(() -> SSOException.forbidden("Not this brand's super admin"));
        return brand;
    }

    // ── Organizations ──────────────────────────────────────────────────

    @GetMapping("/organizations")
    public List<Map<String, Object>> listOrganizations(Authentication authentication) {
        Brand brand = requireSuperAdmin(authentication);
        return orgService.listByBrand(brand.getSlug()).stream().map(this::toOrgSummary).toList();
    }

    record CreateOrgWithAdminRequest(
            @NotBlank(message = "Organization name is required") String orgName,
            @NotBlank(message = "Slug is required") @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, numbers, and hyphens only") String orgSlug,
            @NotBlank @Email(message = "Must be a valid email") String adminEmail,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String adminPassword
    ) {}

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createOrganization(Authentication authentication, @Valid @RequestBody CreateOrgWithAdminRequest req) {
        Brand brand = requireSuperAdmin(authentication);
        // brandSlug is forced from the resolved tenant, never client-supplied
        // — otherwise a super admin could pass a different brand's slug in
        // the body and create an org under a brand they don't manage.
        Organization org = orgService.create(new CreateOrgRequest(req.orgName(), req.orgSlug(), brand.getSlug()));
        userService.create(org.getSlug(), new CreateUserRequest(req.adminEmail(), req.adminPassword(), "ORG_ADMIN"));
        return toOrgSummary(org);
    }

    private Map<String, Object> toOrgSummary(Organization org) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", org.getId());
        summary.put("name", org.getName());
        summary.put("slug", org.getSlug());
        summary.put("active", org.isActive());
        return summary;
    }

    // ── OAuth2 clients ──────────────────────────────────────────────────

    @GetMapping("/clients")
    public List<ClientResponse> listClients(Authentication authentication) {
        Brand brand = requireSuperAdmin(authentication);
        return clientService.listByBrand(brand.getSlug());
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse createClient(Authentication authentication, @Valid @RequestBody CreateClientRequest req) {
        Brand brand = requireSuperAdmin(authentication);
        return clientService.registerForBrand(brand.getSlug(), req);
    }

    @DeleteMapping("/clients/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClient(Authentication authentication, @PathVariable String clientId) {
        Brand brand = requireSuperAdmin(authentication);
        clientService.deleteForBrand(brand.getSlug(), clientId);
    }
}
