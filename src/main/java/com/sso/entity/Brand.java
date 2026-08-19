package com.sso.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A white-label reseller — sits above Organization. Owns exactly one OAuth2
 * client and is the URL/login tenant unit (/{brandSlug}/oauth2/authorize).
 * Organizations are "customers of a brand" (see Organization.brand).
 */
@Entity
@Table(name = "brands")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
