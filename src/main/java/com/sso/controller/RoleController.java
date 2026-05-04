package com.sso.controller;

import com.sso.dto.CreatePermissionRequest;
import com.sso.dto.CreateRoleRequest;
import com.sso.entity.Permission;
import com.sso.entity.Role;
import com.sso.entity.UserAppRole;
import com.sso.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orgs/{slug}")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    // ── Roles ─────────────────────────────────────────────────────────────────

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRole(@PathVariable String slug,
                                          @Valid @RequestBody CreateRoleRequest req) {
        Role role = roleService.createRole(slug, req);
        return roleToMap(role);
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> listRoles(@PathVariable String slug) {
        return roleService.listRoles(slug).stream().map(this::roleToMap).toList();
    }

    @GetMapping("/clients/{clientId}/roles")
    public List<Map<String, Object>> listRolesForClient(@PathVariable String slug,
                                                         @PathVariable String clientId) {
        return roleService.listRolesForClient(slug, clientId).stream().map(this::roleToMap).toList();
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable String slug, @PathVariable UUID roleId) {
        roleService.deleteRole(slug, roleId);
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPermission(@PathVariable String slug,
                                                @Valid @RequestBody CreatePermissionRequest req) {
        Permission p = roleService.createPermission(slug, req);
        return Map.of("id", p.getId(), "name", p.getName(),
                "resource", p.getResource(), "action", p.getAction());
    }

    @GetMapping("/permissions")
    public List<Map<String, Object>> listPermissions(@PathVariable String slug) {
        return roleService.listPermissions(slug).stream()
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(),
                        "resource", p.getResource(), "action", p.getAction()))
                .toList();
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignPermission(@PathVariable String slug,
                                 @PathVariable UUID roleId,
                                 @PathVariable UUID permissionId) {
        roleService.assignPermissionToRole(slug, roleId, permissionId);
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePermission(@PathVariable String slug,
                                 @PathVariable UUID roleId,
                                 @PathVariable UUID permissionId) {
        roleService.revokePermissionFromRole(slug, roleId, permissionId);
    }

    // ── User Role Assignments ─────────────────────────────────────────────────

    @PostMapping("/users/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> assignRole(@PathVariable String slug,
                                          @PathVariable UUID userId,
                                          @PathVariable UUID roleId,
                                          @RequestParam String clientId) {
        UserAppRole uar = roleService.assignRoleToUser(slug, userId, roleId, clientId);
        return Map.of("userId", userId, "roleId", roleId,
                "clientId", clientId, "assignedAt", uar.getAssignedAt());
    }

    @GetMapping("/users/{userId}/roles")
    public List<Map<String, Object>> listUserRoles(@PathVariable String slug,
                                                    @PathVariable UUID userId) {
        return roleService.listUserRoles(slug, userId).stream()
                .map(uar -> Map.<String, Object>of(
                        "roleId", uar.getRole().getId(),
                        "roleName", uar.getRole().getName(),
                        "clientId", uar.getClientId()))
                .toList();
    }

    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(@PathVariable String slug,
                           @PathVariable UUID userId,
                           @PathVariable UUID roleId,
                           @RequestParam String clientId) {
        roleService.revokeRoleFromUser(slug, userId, roleId, clientId);
    }

    private Map<String, Object> roleToMap(Role r) {
        return Map.of(
                "id", r.getId(),
                "name", r.getName(),
                "description", r.getDescription() != null ? r.getDescription() : "",
                "clientId", r.getClientId() != null ? r.getClientId() : "org-level"
        );
    }
}
