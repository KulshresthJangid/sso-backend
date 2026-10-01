package com.sso.service;

import com.sso.auth.RegisteredClientMapper;
import com.sso.dto.ClientResponse;
import com.sso.dto.CreateClientRequest;
import com.sso.entity.Brand;
import com.sso.entity.Organization;
import com.sso.entity.RegisteredClientEntity;
import com.sso.exception.SSOException;
import com.sso.repository.RegisteredClientEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    // Kaizex's actual admin sessions kept getting silently kicked back to
    // login mid-workday — 1 hour was too short for how this app is actually
    // used. 24 hours matches a normal workday without leaving a stale admin
    // session valid indefinitely (refresh tokens already cover longer-lived
    // "stay logged in" via the 30-day refresh TTL below).
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RegisteredClientEntityRepository clientRepo;
    private final OrganizationService orgService;
    private final BrandService brandService;
    private final RegisteredClientMapper mapper;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");
    private static final List<String> DEFAULT_GRANTS = List.of("authorization_code", "refresh_token");

    /** Legacy org-owned client registration — kept for any pre-brand-tier orgs. */
    @Transactional
    public ClientResponse register(String orgSlug, CreateClientRequest req) {
        Organization org = orgService.getBySlug(orgSlug);
        Built built = buildEntity(req);
        built.entity().setOrganization(org);
        clientRepo.save(built.entity());
        return toResponse(built.entity(), built.plainSecret());
    }

    /**
     * Brand-owned client registration — the path new brands (Zoralis, etc.)
     * use. One client per brand; every org under that brand shares it for
     * login purposes (see SsoController's per-brand tenant config on the
     * SMAT side, and SSOTokenCustomizer's brand-then-org resolution here).
     */
    @Transactional
    public ClientResponse registerForBrand(String brandSlug, CreateClientRequest req) {
        Brand brand = brandService.getBySlug(brandSlug);
        Built built = buildEntity(req);
        built.entity().setBrand(brand);
        clientRepo.save(built.entity());
        return toResponse(built.entity(), built.plainSecret());
    }

    /**
     * An org's own Applications/Roles pages need to see BOTH its own
     * (legacy, pre-brand) clients AND its brand's shared client(s) — the
     * white-label onboards the app once at the brand level
     * (registerForBrand), and every org under that brand logs its users
     * into the same app, so permission discovery/role creation for e.g.
     * SMAT has to work from that brand-owned registration, not require
     * each org to separately re-register the same app.
     */
    public List<ClientResponse> listByOrg(String orgSlug) {
        Organization org = orgService.getBySlug(orgSlug);
        List<RegisteredClientEntity> ownClients = clientRepo.findAllByOrganizationId(org.getId());
        List<RegisteredClientEntity> brandClients = org.getBrand() != null
                ? clientRepo.findAllByBrandId(org.getBrand().getId())
                : List.of();
        return java.util.stream.Stream.concat(ownClients.stream(), brandClients.stream())
                .map(e -> toResponse(e, null))
                .toList();
    }

    public List<ClientResponse> listByBrand(String brandSlug) {
        Brand brand = brandService.getBySlug(brandSlug);
        return clientRepo.findAllByBrandId(brand.getId()).stream()
                .map(e -> toResponse(e, null))
                .toList();
    }

    @Transactional
    public void delete(String orgSlug, String clientId) {
        Organization org = orgService.getBySlug(orgSlug);
        clientRepo.findByClientIdAndOrganizationId(clientId, org.getId())
                .ifPresentOrElse(
                        clientRepo::delete,
                        () -> { throw SSOException.notFound("Client not found: " + clientId); }
                );
    }

    @Transactional
    public void deleteForBrand(String brandSlug, String clientId) {
        Brand brand = brandService.getBySlug(brandSlug);
        clientRepo.findByClientIdAndBrandId(clientId, brand.getId())
                .ifPresentOrElse(
                        clientRepo::delete,
                        () -> { throw SSOException.notFound("Client not found: " + clientId); }
                );
    }

    /**
     * tokenSettings is serialized once, at creation, into the DB — it's
     * never recomputed on read. Changing ACCESS_TOKEN_TTL/REFRESH_TOKEN_TTL
     * above only takes effect for clients registered from that point
     * forward; every already-registered client (Kaizex's included) keeps
     * whatever TTL was baked in when it was created. Run once at startup
     * (see TokenSettingsSyncRunner) to bring existing clients in line with
     * the current policy too — idempotent, so it's harmless to run on every
     * boot and self-heals if the policy changes again later.
     */
    @Transactional
    public void syncAllTokenSettings() {
        int updated = 0;
        for (RegisteredClientEntity entity : clientRepo.findAll()) {
            try {
                TokenSettings current = TokenSettings.withSettings(mapper.deserializeSettings(entity.getTokenSettings())).build();
                if (ACCESS_TOKEN_TTL.equals(current.getAccessTokenTimeToLive())
                        && REFRESH_TOKEN_TTL.equals(current.getRefreshTokenTimeToLive())) {
                    continue;
                }
                TokenSettings updatedSettings = TokenSettings.withSettings(current.getSettings())
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                        .build();
                entity.setTokenSettings(mapper.serializeSettings(updatedSettings.getSettings()));
                clientRepo.save(entity);
                updated++;
            } catch (Exception e) {
                log.error("Failed to sync tokenSettings for clientId={}: {}", entity.getClientId(), e.getMessage());
            }
        }
        if (updated > 0) {
            log.info("Synced tokenSettings (accessTokenTTL={}, refreshTokenTTL={}) for {} existing registered client(s)",
                    ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL, updated);
        }
    }

    /** The entity only ever stores the hash — this carries the plaintext back once, at creation. */
    private record Built(RegisteredClientEntity entity, String plainSecret) {}

    private Built buildEntity(CreateClientRequest req) {
        String plainSecret = UUID.randomUUID().toString();
        String hashedSecret = passwordEncoder.encode(plainSecret);

        List<String> scopes = (req.scopes() != null && !req.scopes().isEmpty()) ? req.scopes() : DEFAULT_SCOPES;
        List<String> grants = (req.grantTypes() != null && !req.grantTypes().isEmpty()) ? req.grantTypes() : DEFAULT_GRANTS;

        ClientSettings cs = ClientSettings.builder()
                .requireProofKey(false)
                .requireAuthorizationConsent(false)
                .build();

        TokenSettings ts = TokenSettings.builder()
                .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                .reuseRefreshTokens(false)
                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        RegisteredClientEntity entity = RegisteredClientEntity.builder()
                .id(UUID.randomUUID().toString())
                .clientId(UUID.randomUUID().toString())
                .clientSecret(hashedSecret)
                .clientName(req.clientName() != null ? req.clientName() : "Unnamed App")
                .clientAuthenticationMethods("client_secret_basic")
                .authorizationGrantTypes(String.join(",", grants))
                .redirectUris(req.redirectUris() != null ? String.join(",", req.redirectUris()) : null)
                .scopes(String.join(",", scopes))
                .permissionsUri(req.permissionsUri())
                .clientSettings(mapper.serializeSettings(cs.getSettings()))
                .tokenSettings(mapper.serializeSettings(ts.getSettings()))
                .build();

        return new Built(entity, plainSecret);
    }

    private ClientResponse toResponse(RegisteredClientEntity e, String plainSecret) {
        return new ClientResponse(
                e.getId(),
                e.getClientId(),
                plainSecret,  // null on list, plaintext only on creation
                e.getClientName(),
                e.getOrganization() != null ? e.getOrganization().getId().toString() : null,
                e.getBrand() != null ? e.getBrand().getId().toString() : null,
                e.getRedirectUris() != null ? Arrays.asList(e.getRedirectUris().split(",")) : List.of(),
                Arrays.asList(e.getScopes().split(",")),
                Arrays.asList(e.getAuthorizationGrantTypes().split(",")),
                e.getPermissionsUri()
        );
    }
}
