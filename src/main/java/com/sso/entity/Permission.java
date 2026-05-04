package com.sso.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "permissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"name", "org_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    /** e.g. "issues:read" */
    @Column(nullable = false, length = 100)
    private String name;

    /** e.g. "issues" */
    @Column(nullable = false, length = 100)
    private String resource;

    /** e.g. "read", "write", "delete", "admin" */
    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 500)
    private String description;
}
