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
 * All lookups are scoped to the current org from TenantContext.
 */
@Service
@RequiredArgsConstructor
public class TenantAwareRegisteredClientRepository implements RegisteredClientRepository {

    private final RegisteredClientEntityRepository clientRepo;
    private final RegisteredClientMapper mapper;

    @Override
    public void save(RegisteredClient registeredClient) {
        // Used internally by Spring AS — not called by our code directly
        throw new UnsupportedOperationException(
                "Use ClientService to register clients; direct save not allowed.");
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
        var org = TenantContext.get();
        if (org == null) {
            // Fallback: find globally (used during token introspection without path tenant)
            return clientRepo.findByClientId(clientId)
                    .map(mapper::toRegisteredClient)
                    .orElse(null);
        }
        return clientRepo.findByClientIdAndOrganizationId(clientId, org.getId())
                .map(mapper::toRegisteredClient)
                .orElse(null);
    }

    private boolean isBelongsToCurrentTenant(RegisteredClientEntity e) {
        var org = TenantContext.get();
        return org == null || org.getId().equals(e.getOrganization().getId());
    }
}
