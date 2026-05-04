package com.sso.token;

import com.sso.entity.User;
import com.sso.repository.UserAppRoleRepository;
import com.sso.repository.UserRepository;
import com.sso.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enriches JWTs with tenant and RBAC claims:
 *   org_id, org_slug, org_role, email, roles[], permissions[]
 */
@Component
@RequiredArgsConstructor
public class SSOTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepo;
    private final UserAppRoleRepository userAppRoleRepo;

    @Value("${app.issuer-base-url}")
    private String issuerBaseUrl;

    @Override
    public void customize(JwtEncodingContext context) {
        var org = TenantContext.get();
        if (org == null) return;

        var claims = context.getClaims();

        // Tenant identity claims
        claims.issuer(issuerBaseUrl + "/" + org.getSlug());
        claims.claim("org_id", org.getId().toString());
        claims.claim("org_slug", org.getSlug());

        // User-specific claims (not applicable for client_credentials flow)
        if (context.getPrincipal() instanceof UsernamePasswordAuthenticationToken) {
            String email = context.getPrincipal().getName();
            userRepo.findByEmailAndOrganizationIdAndActiveTrue(email, org.getId())
                    .ifPresent(user -> addUserClaims(claims, user, context));
        }
    }

    private void addUserClaims(
            org.springframework.security.oauth2.jwt.JwtClaimsSet.Builder claims,
            User user,
            JwtEncodingContext context) {

        claims.claim("email", user.getEmail());
        claims.claim("org_role", user.getOrgRole().name());

        // App-specific roles and permissions
        String clientId = context.getRegisteredClient().getClientId();
        var assignments = userAppRoleRepo
                .findWithPermissionsByUserIdAndClientId(user.getId(), clientId);

        List<String> roles = assignments.stream()
                .map(a -> a.getRole().getName())
                .distinct()
                .collect(Collectors.toList());

        List<String> permissions = assignments.stream()
                .flatMap(a -> a.getRole().getPermissions().stream())
                .map(p -> p.getName())
                .distinct()
                .collect(Collectors.toList());

        claims.claim("roles", roles);
        claims.claim("permissions", permissions);
    }
}
