package com.sso.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registered_clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisteredClientEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_id_issued_at", nullable = false)
    private LocalDateTime clientIdIssuedAt;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "client_secret_expires_at")
    private LocalDateTime clientSecretExpiresAt;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_authentication_methods", nullable = false, columnDefinition = "TEXT")
    private String clientAuthenticationMethods;

    @Column(name = "authorization_grant_types", nullable = false, columnDefinition = "TEXT")
    private String authorizationGrantTypes;

    @Column(name = "redirect_uris", columnDefinition = "TEXT")
    private String redirectUris;

    @Column(name = "post_logout_redirect_uris", columnDefinition = "TEXT")
    private String postLogoutRedirectUris;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String scopes;

    @Column(name = "client_settings", nullable = false, columnDefinition = "TEXT")
    private String clientSettings;

    @Column(name = "token_settings", nullable = false, columnDefinition = "TEXT")
    private String tokenSettings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (clientIdIssuedAt == null) clientIdIssuedAt = LocalDateTime.now();
    }
}
