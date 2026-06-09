package com.sso.controller;

import com.sso.dto.*;
import com.sso.entity.Workspace;
import com.sso.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin API for workspace management — called programmatically by Lumen
 * during enterprise onboarding. Enterprise users never interact with this directly.
 */
@RestController
@RequestMapping("/api/admin/orgs/{orgId}/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateWorkspaceRequest req) {
        Workspace ws = workspaceService.create(orgId, req);
        return WorkspaceResponse.from(ws);
    }

    @GetMapping
    public List<WorkspaceResponse> list(@PathVariable UUID orgId) {
        return workspaceService.listByOrg(orgId);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse get(@PathVariable UUID orgId,
                                 @PathVariable UUID workspaceId) {
        return workspaceService.getById(workspaceId);
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID orgId,
                           @PathVariable UUID workspaceId) {
        workspaceService.deactivate(workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable UUID orgId,
                          @PathVariable UUID workspaceId,
                          @Valid @RequestBody AddWorkspaceMemberRequest req) {
        workspaceService.addMember(workspaceId, req);
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID orgId,
                             @PathVariable UUID workspaceId,
                             @PathVariable UUID userId) {
        workspaceService.removeMember(workspaceId, userId);
    }

    @PostMapping("/{workspaceId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public void assignRole(@PathVariable UUID orgId,
                           @PathVariable UUID workspaceId,
                           @Valid @RequestBody AssignWorkspaceRoleRequest req) {
        workspaceService.assignRole(workspaceId, req);
    }

    @DeleteMapping("/{workspaceId}/roles/{userId}/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRole(@PathVariable UUID orgId,
                           @PathVariable UUID workspaceId,
                           @PathVariable UUID userId,
                           @PathVariable UUID roleId) {
        workspaceService.removeRole(workspaceId, userId, roleId);
    }
}
