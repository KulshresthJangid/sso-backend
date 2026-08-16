package com.sso.service;

import com.sso.dto.CreatePermissionRequest;
import com.sso.dto.CreateRoleRequest;
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
public class RoleService {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final UserWorkspaceRoleRepository userWorkspaceRoleRepo;
    private final WorkspaceMemberRepository workspaceMemberRepo;
    private final UserRepository userRepo;
    private final OrganizationService orgService;

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Transactional
    public Role createRole(String orgSlug, CreateRoleRequest req) {
        Organization org = orgService.getBySlug(orgSlug);
        return roleRepo.save(Role.builder()
                .organization(org)
                .clientId(req.clientId())   // null = org-level
                .name(req.name())
                .description(req.description())
                .build());
    }

    public List<Role> listRoles(String orgSlug) {
        Organization org = orgService.getBySlug(orgSlug);
        return roleRepo.findAllByOrganizationId(org.getId());
    }

    public List<Role> listRolesForClient(String orgSlug, String clientId) {
        Organization org = orgService.getBySlug(orgSlug);
        return roleRepo.findAllByOrganizationIdAndClientId(org.getId(), clientId);
    }

    @Transactional
    public void deleteRole(String orgSlug, UUID roleId) {
        Organization org = orgService.getBySlug(orgSlug);
        Role role = roleRepo.findById(roleId)
                .filter(r -> r.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("Role not found"));
        roleRepo.delete(role);
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    @Transactional
    public Permission createPermission(String orgSlug, CreatePermissionRequest req) {
        Organization org = orgService.getBySlug(orgSlug);
        if (permissionRepo.existsByNameAndOrganizationId(req.name(), org.getId())) {
            throw SSOException.conflict("Permission already exists: " + req.name());
        }
        return permissionRepo.save(Permission.builder()
                .organization(org)
                .name(req.name())
                .resource(req.resource())
                .action(req.action())
                .description(req.description())
                .build());
    }

    public List<Permission> listPermissions(String orgSlug) {
        Organization org = orgService.getBySlug(orgSlug);
        return permissionRepo.findAllByOrganizationId(org.getId());
    }

    @Transactional
    public void assignPermissionToRole(String orgSlug, UUID roleId, UUID permissionId) {
        Organization org = orgService.getBySlug(orgSlug);
        Role role = roleRepo.findById(roleId)
                .filter(r -> r.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("Role not found"));
        Permission perm = permissionRepo.findById(permissionId)
                .filter(p -> p.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("Permission not found"));
        role.getPermissions().add(perm);
        roleRepo.save(role);
    }

    @Transactional
    public void revokePermissionFromRole(String orgSlug, UUID roleId, UUID permissionId) {
        Organization org = orgService.getBySlug(orgSlug);
        Role role = roleRepo.findById(roleId)
                .filter(r -> r.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("Role not found"));
        role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        roleRepo.save(role);
    }

    // ── User-Role Assignments ─────────────────────────────────────────────────
    //
    // Assigns into user_workspace_roles — the table SSOTokenCustomizer actually
    // reads to build a JWT's permissions[] claim. (This used to write
    // user_app_roles instead, a table nothing else in the codebase reads —
    // assigning a role via this endpoint silently did nothing for the user's
    // real permissions. See git history for the full story.)
    //
    // user_workspace_roles is workspace-scoped and this endpoint isn't (no
    // workspace picker exists in sso-frontend yet), so — same assumption
    // SSOTokenCustomizer's own workspace fallback already makes — we resolve
    // the user's first/only workspace membership. Fine while every org has
    // exactly one real workspace; revisit if/when multi-workspace orgs exist.

    @Transactional
    public UserWorkspaceRole assignRoleToUser(String orgSlug, UUID userId, UUID roleId, String clientId) {
        Organization org = orgService.getBySlug(orgSlug);
        User user = userRepo.findById(userId)
                .filter(u -> u.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("User not found"));
        Role role = roleRepo.findById(roleId)
                .filter(r -> r.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("Role not found"));
        Workspace workspace = resolveWorkspace(userId);

        return userWorkspaceRoleRepo.save(UserWorkspaceRole.builder()
                .user(user)
                .role(role)
                .workspace(workspace)
                .clientId(clientId)
                .build());
    }

    public List<UserWorkspaceRole> listUserRoles(String orgSlug, UUID userId) {
        Organization org = orgService.getBySlug(orgSlug);
        userRepo.findById(userId)
                .filter(u -> u.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("User not found"));
        Workspace workspace = resolveWorkspace(userId);
        return userWorkspaceRoleRepo.findAllByWorkspaceIdAndUserId(workspace.getId(), userId);
    }

    @Transactional
    public void revokeRoleFromUser(String orgSlug, UUID userId, UUID roleId, String clientId) {
        Workspace workspace = resolveWorkspace(userId);
        userWorkspaceRoleRepo.deleteByWorkspaceIdAndUserIdAndRoleId(workspace.getId(), userId, roleId);
    }

    private Workspace resolveWorkspace(UUID userId) {
        return workspaceMemberRepo.findAllByUserIdWithWorkspace(userId)
                .stream()
                .findFirst()
                .map(WorkspaceMember::getWorkspace)
                .orElseThrow(() -> SSOException.conflict(
                        "User has no workspace — add them to a workspace before assigning roles"));
    }
}
