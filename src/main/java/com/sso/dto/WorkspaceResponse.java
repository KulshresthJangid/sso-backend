package com.sso.dto;

import com.sso.entity.Workspace;

import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        String slug,
        UUID orgId,
        String orgSlug,
        boolean active
) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(
                w.getId(),
                w.getName(),
                w.getSlug(),
                w.getOrganization().getId(),
                w.getOrganization().getSlug(),
                w.isActive()
        );
    }
}
