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
    private final UserAppRoleRepository userAppRoleRepo;
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

    @Transactional
    public UserAppRole assignRoleToUser(String orgSlug, UUID userId, UUID roleId, String clientId) {
        Organization org = orgService.getBySlug(orgSlug);
        User user = userRepo.findById(userId)
                .filter(u -> u.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("User not found"));
        Role role = roleRepo.findById(roleId)
                .filter(r -> r.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("Role not found"));

        return userAppRoleRepo.save(UserAppRole.builder()
                .user(user)
                .role(role)
                .organization(org)
                .clientId(clientId)
                .build());
    }

    public List<UserAppRole> listUserRoles(String orgSlug, UUID userId) {
        Organization org = orgService.getBySlug(orgSlug);
        userRepo.findById(userId)
                .filter(u -> u.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("User not found"));
        return userAppRoleRepo.findAllByOrganizationId(org.getId());
    }

    @Transactional
    public void revokeRoleFromUser(String orgSlug, UUID userId, UUID roleId, String clientId) {
        userAppRoleRepo.deleteByUserIdAndRoleIdAndClientId(userId, roleId, clientId);
    }
}
