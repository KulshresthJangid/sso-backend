package com.sso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignWorkspaceRoleRequest(
        @NotNull UUID userId,
        @NotNull UUID roleId,
        @NotBlank String clientId
) {}
