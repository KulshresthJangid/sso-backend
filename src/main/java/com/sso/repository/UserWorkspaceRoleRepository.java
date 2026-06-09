package com.sso.repository;

import com.sso.entity.UserWorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserWorkspaceRoleRepository extends JpaRepository<UserWorkspaceRole, UUID> {

    List<UserWorkspaceRole> findAllByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    void deleteByWorkspaceIdAndUserIdAndRoleId(UUID workspaceId, UUID userId, UUID roleId);

    /** Eagerly fetch roles + permissions for JWT customizer — workspace-scoped */
    @Query("""
            SELECT uwr FROM UserWorkspaceRole uwr
            JOIN FETCH uwr.role r
            JOIN FETCH r.permissions
            WHERE uwr.workspace.id = :workspaceId
              AND uwr.user.id = :userId
              AND uwr.clientId = :clientId
            """)
    List<UserWorkspaceRole> findWithPermissionsByWorkspaceUserClient(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("clientId") String clientId);
}
