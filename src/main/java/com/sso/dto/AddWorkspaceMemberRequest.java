package com.sso.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWorkspaceMemberRequest(
        @NotNull UUID userId
) {}
