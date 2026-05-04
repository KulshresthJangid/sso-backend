package com.sso.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(
        @NotBlank String name,
        String description,
        String clientId   // null = org-level role, set = app-specific role
) {}
