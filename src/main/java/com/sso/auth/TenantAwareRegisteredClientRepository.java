package com.sso.auth;

import com.sso.entity.RegisteredClientEntity;
import com.sso.repository.RegisteredClientEntityRepository;
import com.sso.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

/**
 * Implements Spring's RegisteredClientRepository with tenant isolation.
 * All lookups are scoped to the current brand from TenantContext (brands own
 * OAuth2 clients now, not individual orgs — see RegisteredClientEntity).
 */
@Service
@RequiredArgsConstructor
public class TenantAwareRegisteredClientRepository implements RegisteredClientRepository {

    private final RegisteredClientEntityRepository clientRepo;
    private final RegisteredClientMapper mapper;

    @Override
    public void save(RegisteredClient registeredClient) {
        // Spring AS calls save() to upgrade BCrypt encoding strength on successful auth.
        // Only allow updates to an existing client's secret — reject brand-new inserts.
        clientRepo.findById(registeredClient.getId()).ifPresentOrElse(
                entity -> {
                    if (registeredClient.getClientSecret() != null) {
                        entity.setClientSecret(registeredClient.getClientSecret());
                        clientRepo.save(entity);
                    }
                },
                () -> { throw new UnsupportedOperationException(
                        "Use ClientService to register new clients."); }
        );
    }

    @Override
    public RegisteredClient findById(String id) {
        return clientRepo.findById(id)
                .filter(e -> isBelongsToCurrentTenant(e))
                .map(mapper::toRegisteredClient)
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        var brand = TenantContext.get();
        if (brand == null) {
            // Fallback: find globally (used during token introspection without path tenant)
            return clientRepo.findByClientId(clientId)
                    .map(mapper::toRegisteredClient)
                    .orElse(null);
        }
        return clientRepo.findByClientIdAndBrandId(clientId, brand.getId())
                .map(mapper::toRegisteredClient)
                .orElse(null);
    }

    private boolean isBelongsToCurrentTenant(RegisteredClientEntity e) {
        var brand = TenantContext.get();
        if (brand == null) return true;
        // Brand-owned client (the norm going forward) — direct comparison.
        if (e.getBrand() != null) return brand.getId().equals(e.getBrand().getId());
        // Legacy org-owned client with no brand_id backfilled — resolve via its org.
        return e.getOrganization() != null && e.getOrganization().getBrand() != null
                && brand.getId().equals(e.getOrganization().getBrand().getId());
    }
}
