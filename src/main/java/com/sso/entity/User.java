package com.sso.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = {"email", "org_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Nullable — a brand-level SUPER_ADMIN (see OrgRole below) isn't a
    // member of any one org, they sit above all of a brand's orgs, so this
    // is null for them and `brand` is set instead. Every other user keeps
    // this required in practice (enforced at the service layer, not the DB,
    // to keep the column itself simply nullable).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization organization;

    // Set only for a brand-level SUPER_ADMIN — null for ordinary org users
    // (their brand is reached transitively via organization.brand instead).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_role", nullable = false, length = 50)
    @Builder.Default
    private OrgRole orgRole = OrgRole.ORG_MEMBER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum OrgRole {
        ORG_ADMIN,
        ORG_MEMBER,
        // Brand-level — manages every org under one brand (see
        // SuperAdminService). Exactly one per brand (enforced by a partial
        // unique index, see V10 migration); `organization` is null,
        // `brand` is set.
        SUPER_ADMIN
    }
}
