package com.sso.token;

import com.sso.entity.User;
import com.sso.entity.Workspace;
import com.sso.repository.UserRepository;
import com.sso.repository.UserWorkspaceRoleRepository;
import com.sso.repository.WorkspaceMemberRepository;
import com.sso.repository.WorkspaceRepository;
import com.sso.repository.RegisteredClientEntityRepository;
import com.sso.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enriches JWTs with tenant and workspace RBAC claims:
 *   org_id, org_slug, workspace_id, workspace_slug, workspace_name,
 *   email, permissions[]
 *
 * workspace_id is read from the authorization request parameter so the
 * frontend controls which workspace context the token carries.
 * Permissions are resolved from user_workspace_roles for that workspace.
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

    @Override
    public void customize(JwtEncodingContext context) {
        var org = TenantContext.get();
        if (org == null) {
            var registeredClient = context.getRegisteredClient();
            if (registeredClient != null) {
                var clientEntityOpt = clientRepo.findByClientId(registeredClient.getClientId());
                if (clientEntityOpt.isPresent()) {
                    org = clientEntityOpt.get().getOrganization();
                    log.info("Resolved org '{}' from registered client id: {}", org.getSlug(), registeredClient.getClientId());
                }
            }
        }
        if (org == null) return;

        var claims = context.getClaims();

        claims.issuer(issuerBaseUrl + "/" + org.getSlug());
        claims.claim("org_id", org.getId().toString());
        claims.claim("org_slug", org.getSlug());

        if (!(context.getPrincipal() instanceof UsernamePasswordAuthenticationToken)) return;

        String email = context.getPrincipal().getName();
        userRepo.findByEmailAndOrganizationIdAndActiveTrue(email, org.getId())
                .ifPresent(user -> addUserClaims(claims, user, context));
    }

    private void addUserClaims(
            org.springframework.security.oauth2.jwt.JwtClaimsSet.Builder claims,
            User user,
            JwtEncodingContext context) {

        claims.claim("email", user.getEmail());
        claims.claim("user_id", user.getId().toString());
        claims.claim("org_role", user.getOrgRole().name());

        // Extract workspace_id from the original authorization request
        String workspaceId = extractWorkspaceId(context);
        if (workspaceId == null) {
            // No workspace context — inject empty permissions so JWT is still valid
            claims.claim("permissions", List.of());
            return;
        }

        UUID wsUuid;
        try {
            wsUuid = UUID.fromString(workspaceId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid workspace_id in auth request: {}", workspaceId);
            claims.claim("permissions", List.of());
            return;
        }

        Workspace workspace = workspaceRepo.findByIdAndActiveTrue(wsUuid).orElse(null);
        if (workspace == null) {
            log.warn("workspace_id {} not found or inactive", workspaceId);
            claims.claim("permissions", List.of());
            return;
        }

        // Verify the user is actually a member of this workspace
        if (!workspaceMemberRepo.existsByWorkspaceIdAndUserId(wsUuid, user.getId())) {
            log.warn("User {} is not a member of workspace {}", user.getId(), workspaceId);
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
