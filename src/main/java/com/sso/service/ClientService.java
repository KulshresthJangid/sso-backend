package com.sso.service;

import com.sso.auth.RegisteredClientMapper;
import com.sso.dto.ClientResponse;
import com.sso.dto.CreateClientRequest;
import com.sso.entity.Organization;
import com.sso.entity.RegisteredClientEntity;
import com.sso.exception.SSOException;
import com.sso.repository.RegisteredClientEntityRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class ClientService {

    private final RegisteredClientEntityRepository clientRepo;
    private final OrganizationService orgService;
    private final RegisteredClientMapper mapper;
    private final PasswordEncoder passwordEncoder;

    private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");
    private static final List<String> DEFAULT_GRANTS = List.of("authorization_code", "refresh_token");

    @Transactional
    public ClientResponse register(String orgSlug, CreateClientRequest req) {
        Organization org = orgService.getBySlug(orgSlug);

        String plainSecret = UUID.randomUUID().toString();
        String hashedSecret = passwordEncoder.encode(plainSecret);

        List<String> scopes = (req.scopes() != null && !req.scopes().isEmpty()) ? req.scopes() : DEFAULT_SCOPES;
        List<String> grants = (req.grantTypes() != null && !req.grantTypes().isEmpty()) ? req.grantTypes() : DEFAULT_GRANTS;

        ClientSettings cs = ClientSettings.builder()
                .requireProofKey(false)
                .requireAuthorizationConsent(false)
                .build();

        TokenSettings ts = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        RegisteredClientEntity entity = RegisteredClientEntity.builder()
                .id(UUID.randomUUID().toString())
                .organization(org)
                .clientId(UUID.randomUUID().toString())
                .clientSecret(hashedSecret)
                .clientName(req.clientName() != null ? req.clientName() : "Unnamed App")
                .clientAuthenticationMethods("client_secret_basic")
                .authorizationGrantTypes(String.join(",", grants))
                .redirectUris(req.redirectUris() != null ? String.join(",", req.redirectUris()) : null)
                .scopes(String.join(",", scopes))
                .clientSettings(mapper.serializeSettings(cs.getSettings()))
                .tokenSettings(mapper.serializeSettings(ts.getSettings()))
                .build();

        clientRepo.save(entity);

        return toResponse(entity, plainSecret);
    }

    public List<ClientResponse> listByOrg(String orgSlug) {
        Organization org = orgService.getBySlug(orgSlug);
        return clientRepo.findAllByOrganizationId(org.getId()).stream()
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

    private ClientResponse toResponse(RegisteredClientEntity e, String plainSecret) {
        return new ClientResponse(
                e.getId(),
                e.getClientId(),
                plainSecret,  // null on list, plaintext only on creation
                e.getClientName(),
                e.getOrganization().getId().toString(),
                e.getRedirectUris() != null ? Arrays.asList(e.getRedirectUris().split(",")) : List.of(),
                Arrays.asList(e.getScopes().split(",")),
                Arrays.asList(e.getAuthorizationGrantTypes().split(","))
        );
    }
}
