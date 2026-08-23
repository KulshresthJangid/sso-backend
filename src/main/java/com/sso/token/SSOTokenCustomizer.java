package com.sso.token;

import com.sso.entity.Brand;
import com.sso.entity.User;
import com.sso.entity.Workspace;
import com.sso.repository.UserRepository;
import com.sso.repository.UserWorkspaceRoleRepository;
import com.sso.repository.WorkspaceMemberRepository;
import com.sso.repository.WorkspaceRepository;
import com.sso.repository.RegisteredClientEntityRepository;
import com.sso.entity.User.OrgRole;
import com.sso.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enriches JWTs with tenant and workspace RBAC claims:
 *   brand_id, brand_slug, org_id, org_slug, workspace_id, workspace_slug,
 *   workspace_name, email, permissions[]
 *
 * workspace_id is read from the authorization request parameter so the
 * frontend controls which workspace context the token carries.
 * Permissions are resolved from user_workspace_roles for that workspace.
 *
 * Brand replaced Organization as the URL/OAuth2 tenant unit — TenantContext
 * now holds a Brand, not an Organization directly. org_id/org_slug are only
 * knowable once a specific User is resolved (a brand groups many orgs), so
 * those two claims moved into addUserClaims(); brand_id/brand_slug are set
 * unconditionally since the brand is known regardless of whether a user
 * ends up resolving.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SSOTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepo;
    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository workspaceMemberRepo;
    private final UserWorkspaceRoleRepository userWorkspaceRoleRepo;
    private final RegisteredClientEntityRepository clientRepo;

    @Value("${app.issuer-base-url}")
    private String issuerBaseUrl;

    // Runs deep inside Spring Security's OAuth2 token-generation pipeline
    // (OAuth2TokenEndpointFilter → OAuth2AuthorizationCodeAuthenticationProvider),
    // not a normal @Transactional service call — open-in-view doesn't
    // reliably cover it, so user.getOrganization() (LAZY) throws
    // LazyInitializationException ("no Session") once the short-lived
    // session from the userRepo lookup above closes. @Transactional here
    // keeps one session open for the whole customize() call, including the
    // addUserClaims() lazy nav below.
    @Override
    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        var brand = TenantContext.get();
        if (brand == null) {
            var registeredClient = context.getRegisteredClient();
            if (registeredClient != null) {
                var clientEntityOpt = clientRepo.findByClientId(registeredClient.getClientId());
                if (clientEntityOpt.isPresent()) {
                    var clientEntity = clientEntityOpt.get();
                    // Brand-owned client → use it directly; legacy org-owned
                    // client (pre-brand-tier data) → resolve via its org.
                    brand = clientEntity.getBrand() != null
                            ? clientEntity.getBrand()
                            : (clientEntity.getOrganization() != null ? clientEntity.getOrganization().getBrand() : null);
                    if (brand != null) {
                        log.info("Resolved brand '{}' from registered client id: {}", brand.getSlug(), registeredClient.getClientId());
                    }
                }
            }
        }
        if (brand == null) return;

        var claims = context.getClaims();

        claims.issuer(issuerBaseUrl + "/" + brand.getSlug());
        claims.claim("brand_id", brand.getId().toString());
        claims.claim("brand_slug", brand.getSlug());

        if (!(context.getPrincipal() instanceof UsernamePasswordAuthenticationToken)) return;

        String email = context.getPrincipal().getName();
        Brand finalBrand = brand;
        userRepo.findFirstByEmailAndOrganization_Brand_IdAndActiveTrue(email, brand.getId())
                .or(() -> userRepo.findFirstByEmailAndBrand_IdAndOrgRoleAndActiveTrue(email, finalBrand.getId(), OrgRole.SUPER_ADMIN))
                .ifPresent(user -> addUserClaims(claims, user, context));
    }

    private void addUserClaims(
            org.springframework.security.oauth2.jwt.JwtClaimsSet.Builder claims,
            User user,
            JwtEncodingContext context) {

        claims.claim("email", user.getEmail());
        claims.claim("user_id", user.getId().toString());
        claims.claim("org_role", user.getOrgRole().name());

        // Brand-level SUPER_ADMIN — not in any org, so there's no
        // org_id/org_slug/workspace to resolve. It gets a synthetic
        // BRAND_SUPER_ADMIN permission plus every workspace id under the
        // brand (baked in at token-mint time, same as every other claim
        // here — a brand's org list only changes as often as this token
        // does, an acceptable staleness window for an admin console).
        if (user.getOrgRole() == com.sso.entity.User.OrgRole.SUPER_ADMIN) {
            List<String> workspaceIds = workspaceRepo.findAllByOrganization_Brand_IdAndActiveTrue(user.getBrand().getId())
                    .stream()
                    .map(ws -> ws.getId().toString())
                    .collect(Collectors.toList());
            claims.claim("brand_workspace_ids", workspaceIds);
            claims.claim("permissions", List.of("BRAND_SUPER_ADMIN"));
            return;
        }

        claims.claim("org_id", user.getOrganization().getId().toString());
        claims.claim("org_slug", user.getOrganization().getSlug());

        // Extract workspace_id from the original authorization request. No
        // caller (SMAT's SsoController.login(), the SSO frontend's own login
        // form) actually sends this today — none of them have a workspace
        // picker — so this was previously *always* null for every login,
        // which meant every JWT minted had permissions:[] regardless of what
        // roles were actually assigned. Confirmed via
        // KAIZEX_FRONTEND_REDESIGN_PLAN.md Phase 7's testing: this broke
        // Buckets, AI Config, and Chat identically for every user.
        //
        // Fix: fall back to the user's own workspace when the caller didn't
        // ask for a specific one. Every org has exactly one real workspace
        // today, so "first membership" is a safe, non-lossy default; a
        // caller that DOES send workspace_id (e.g. a future workspace
        // switcher) still takes precedence untouched above this fallback.
        String workspaceId = extractWorkspaceId(context);
        UUID wsUuid;
        if (workspaceId == null) {
            wsUuid = workspaceMemberRepo.findAllByUserIdWithWorkspace(user.getId())
                    .stream()
                    .findFirst()
                    .map(wm -> wm.getWorkspace().getId())
                    .orElse(null);
            if (wsUuid == null) {
                log.warn("User {} has no workspace_id in the auth request and belongs to no workspace", user.getId());
                claims.claim("permissions", List.of());
                return;
            }
        } else {
            try {
                wsUuid = UUID.fromString(workspaceId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid workspace_id in auth request: {}", workspaceId);
                claims.claim("permissions", List.of());
                return;
            }
        }

        Workspace workspace = workspaceRepo.findByIdAndActiveTrue(wsUuid).orElse(null);
        if (workspace == null) {
            log.warn("workspace_id {} not found or inactive", wsUuid);
            claims.claim("permissions", List.of());
            return;
        }

        // Verify the user is actually a member of this workspace
        if (!workspaceMemberRepo.existsByWorkspaceIdAndUserId(wsUuid, user.getId())) {
            log.warn("User {} is not a member of workspace {}", user.getId(), wsUuid);
            claims.claim("permissions", List.of());
            return;
        }

        claims.claim("workspace_id", workspace.getId().toString());
        claims.claim("workspace_slug", workspace.getSlug());
        claims.claim("workspace_name", workspace.getName());

        String clientId = context.getRegisteredClient().getClientId();
        List<String> permissions = userWorkspaceRoleRepo
                .findWithPermissionsByWorkspaceUserClient(wsUuid, user.getId(), clientId)
                .stream()
                .flatMap(uwr -> uwr.getRole().getPermissions().stream())
                .map(p -> p.getName())
                .distinct()
                .collect(Collectors.toList());

        claims.claim("permissions", permissions);
    }

    private String extractWorkspaceId(JwtEncodingContext context) {
        var authorization = context.getAuthorization();
        if (authorization == null) return null;

        OAuth2AuthorizationRequest authRequest = authorization.getAttribute(
                OAuth2AuthorizationRequest.class.getName());
        if (authRequest == null) return null;

        Object wsId = authRequest.getAdditionalParameters().get("workspace_id");
        return wsId != null ? wsId.toString() : null;
    }
}
