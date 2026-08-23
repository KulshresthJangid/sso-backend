package com.sso.service;

import com.sso.dto.PermissionCatalogEntry;
import com.sso.entity.RegisteredClientEntity;
import com.sso.exception.SSOException;
import com.sso.repository.RegisteredClientEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Permission discovery — the onboarding contract for any application that
 * registers as an OAuth2 client with SSO. Fixes what used to be the only
 * way to create a Permission: an org admin typing a name/resource/action by
 * hand and hoping it exactly matches whatever string the app actually
 * checks via hasAuthority(...)/hasPermission() — no validation, no
 * discoverability, easy to get subtly wrong (see SMAT's SUPER_ADMIN_OWNER/
 * BRAND_SUPER_ADMIN authorities for two real examples of exact-match-or-
 * nothing strings).
 *
 * The contract: a client's registered `permissionsUri` (see
 * RegisteredClientEntity), when set, must respond to a plain GET with:
 *
 *   200 OK
 *   [
 *     {"name": "chat:read", "resource": "chat", "action": "read", "description": "..."},
 *     ...
 *   ]
 *
 * No auth required on that endpoint — it's describing the app's
 * capabilities, not returning anything org-private. See
 * marketing/kaizex/SMAT's PermissionCatalogController for the reference
 * implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCatalogService {

    private final RegisteredClientEntityRepository clientRepo;

    /** Fetches the live catalog from a client's declared permissionsUri — does not persist anything. */
    public List<PermissionCatalogEntry> fetchCatalog(String clientId) {
        RegisteredClientEntity client = clientRepo.findByClientId(clientId)
                .orElseThrow(() -> SSOException.notFound("Client not found: " + clientId));

        if (client.getPermissionsUri() == null || client.getPermissionsUri().isBlank()) {
            throw SSOException.badRequest("This app didn't declare a permissionsUri at registration — nothing to sync.");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            PermissionCatalogEntry[] entries = restTemplate.getForObject(client.getPermissionsUri(), PermissionCatalogEntry[].class);
            return entries != null ? List.of(entries) : List.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch permission catalog from {}: {}", client.getPermissionsUri(), e.getMessage());
            throw SSOException.badRequest("Could not reach this app's permission catalog: " + e.getMessage());
        }
    }
}
