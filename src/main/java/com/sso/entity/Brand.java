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

    /** MINIMAL | AURORA | MIDNIGHT | BENTO — see kaizex-frontend's landing template catalog. */
    @Column(name = "landing_template", nullable = false, length = 20)
    @Builder.Default
    private String landingTemplate = "MINIMAL";

    /** MINIMAL | AURORA | MIDNIGHT | BENTO — see kaizex-frontend's dashboardThemes.ts. */
    @Column(name = "dashboard_template", nullable = false, length = 20)
    @Builder.Default
    private String dashboardTemplate = "MINIMAL";

    /** Optional override of the landing template's own font — null means "use the template's default". See kaizex-frontend's lib/fonts.ts for the curated set. */
    @Column(name = "landing_font", length = 30)
    private String landingFont;

    /** Optional override of the dashboard template's own font — null means "use the template's default". */
    @Column(name = "dashboard_font", length = 30)
    private String dashboardFont;

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
