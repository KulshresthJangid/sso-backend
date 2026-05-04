package com.sso.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissionRequest(
        @NotBlank String name,
        @NotBlank String resource,
        @NotBlank String action,
        String description
) {}
