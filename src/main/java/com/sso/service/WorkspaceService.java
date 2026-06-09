package com.sso.service;

import com.sso.dto.*;
import com.sso.entity.*;
import com.sso.exception.SSOException;
import com.sso.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository memberRepo;
    private final UserWorkspaceRoleRepository uwrRepo;
    private final OrganizationRepository orgRepo;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;

    @Transactional
    public Workspace create(UUID orgId, CreateWorkspaceRequest req) {
        Organization org = orgRepo.findById(orgId)
                .orElseThrow(() -> SSOException.notFound("Organization not found: " + orgId));
        if (workspaceRepo.existsByOrganizationIdAndSlug(orgId, req.slug())) {
            throw SSOException.conflict("Workspace slug already taken: " + req.slug());
        }
        return workspaceRepo.save(Workspace.builder()
                .organization(org)
                .name(req.name())
                .slug(req.slug())
                .build());
    }

    public List<WorkspaceResponse> listByOrg(UUID orgId) {
        return workspaceRepo.findAllByOrganizationIdAndActiveTrue(orgId)
                .stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    public WorkspaceResponse getById(UUID workspaceId) {
        Workspace ws = workspaceRepo.findByIdAndActiveTrue(workspaceId)
                .orElseThrow(() -> SSOException.notFound("Workspace not found: " + workspaceId));
        return WorkspaceResponse.from(ws);
    }

    @Transactional
    public void deactivate(UUID workspaceId) {
        Workspace ws = workspaceRepo.findByIdAndActiveTrue(workspaceId)
                .orElseThrow(() -> SSOException.notFound("Workspace not found: " + workspaceId));
        ws.setActive(false);
        workspaceRepo.save(ws);
    }

    @Transactional
    public void addMember(UUID workspaceId, AddWorkspaceMemberRequest req) {
        Workspace ws = workspaceRepo.findByIdAndActiveTrue(workspaceId)
                .orElseThrow(() -> SSOException.notFound("Workspace not found: " + workspaceId));
        User user = userRepo.findById(req.userId())
                .orElseThrow(() -> SSOException.notFound("User not found: " + req.userId()));
        if (memberRepo.existsByWorkspaceIdAndUserId(workspaceId, req.userId())) {
            throw SSOException.conflict("User is already a member of this workspace");
        }
        memberRepo.save(WorkspaceMember.builder()
                .workspace(ws)
                .user(user)
                .build());
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID userId) {
        if (!memberRepo.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw SSOException.notFound("Member not found in workspace");
        }
        memberRepo.deleteByWorkspaceIdAndUserId(workspaceId, userId);
        // also clean up their role assignments in this workspace
        uwrRepo.findAllByWorkspaceIdAndUserId(workspaceId, userId)
                .forEach(uwr -> uwrRepo.deleteByWorkspaceIdAndUserIdAndRoleId(
                        workspaceId, userId, uwr.getRole().getId()));
    }

    @Transactional
    public void assignRole(UUID workspaceId, AssignWorkspaceRoleRequest req) {
        Workspace ws = workspaceRepo.findByIdAndActiveTrue(workspaceId)
                .orElseThrow(() -> SSOException.notFound("Workspace not found: " + workspaceId));
        if (!memberRepo.existsByWorkspaceIdAndUserId(workspaceId, req.userId())) {
            throw SSOException.conflict("User must be a workspace member before assigning roles");
        }
        User user = userRepo.findById(req.userId())
                .orElseThrow(() -> SSOException.notFound("User not found: " + req.userId()));
        Role role = roleRepo.findById(req.roleId())
                .orElseThrow(() -> SSOException.notFound("Role not found: " + req.roleId()));
        uwrRepo.save(UserWorkspaceRole.builder()
                .workspace(ws)
                .user(user)
                .role(role)
                .clientId(req.clientId())
                .build());
    }

    @Transactional
    public void removeRole(UUID workspaceId, UUID userId, UUID roleId) {
        uwrRepo.deleteByWorkspaceIdAndUserIdAndRoleId(workspaceId, userId, roleId);
    }

    /** All workspaces the calling user is a member of (for workspace switcher) */
    public List<WorkspaceResponse> getMyWorkspaces(UUID userId) {
        return memberRepo.findAllByUserIdWithWorkspace(userId)
                .stream()
                .map(wm -> WorkspaceResponse.from(wm.getWorkspace()))
                .toList();
    }
}
