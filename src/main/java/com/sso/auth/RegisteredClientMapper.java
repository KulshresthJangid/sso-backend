package com.sso.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sso.entity.RegisteredClientEntity;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;

/**
 * Converts between our JPA RegisteredClientEntity and Spring's RegisteredClient.
 * Uses a Jackson ObjectMapper configured with Spring Security's type modules
 * so that ClientSettings / TokenSettings serialize/deserialize correctly.
 */
@Component
public class RegisteredClientMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public RegisteredClientMapper() {
        this.objectMapper = new ObjectMapper();
        ClassLoader cl = RegisteredClientMapper.class.getClassLoader();
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(cl));
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    public RegisteredClient toRegisteredClient(RegisteredClientEntity e) {
        try {
            Map<String, Object> csMap = objectMapper.readValue(e.getClientSettings(), MAP_TYPE);
            Map<String, Object> tsMap = objectMapper.readValue(e.getTokenSettings(), MAP_TYPE);

            var builder = RegisteredClient.withId(e.getId())
                    .clientId(e.getClientId())
                    .clientIdIssuedAt(e.getClientIdIssuedAt().toInstant(ZoneOffset.UTC))
                    .clientName(e.getClientName())
                    .clientSettings(ClientSettings.withSettings(csMap).build())
                    .tokenSettings(TokenSettings.withSettings(tsMap).build());

            if (e.getClientSecret() != null) {
                builder.clientSecret(e.getClientSecret());
            }

            Arrays.stream(e.getClientAuthenticationMethods().split(","))
                    .map(String::trim)
                    .map(ClientAuthenticationMethod::new)
                    .forEach(builder::clientAuthenticationMethod);

            Arrays.stream(e.getAuthorizationGrantTypes().split(","))
                    .map(String::trim)
                    .map(AuthorizationGrantType::new)
                    .forEach(builder::authorizationGrantType);

            if (e.getRedirectUris() != null) {
                Arrays.stream(e.getRedirectUris().split(","))
                        .map(String::trim)
                        .forEach(builder::redirectUri);
            }

            if (e.getPostLogoutRedirectUris() != null) {
                Arrays.stream(e.getPostLogoutRedirectUris().split(","))
                        .map(String::trim)
                        .forEach(builder::postLogoutRedirectUri);
            }

            Arrays.stream(e.getScopes().split(","))
                    .map(String::trim)
                    .forEach(builder::scope);

            return builder.build();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map RegisteredClientEntity id=" + e.getId(), ex);
        }
    }

    public String serializeSettings(Object settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize settings", ex);
        }
    }
}
