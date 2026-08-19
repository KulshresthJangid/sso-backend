package com.sso.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Column(name = "is_platform", nullable = false)
    @Builder.Default
    private boolean platform = false;

    /** Nullable — which white-label reseller this org is a customer of. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Plan {
        FREE, PRO, ENTERPRISE
    }
}
