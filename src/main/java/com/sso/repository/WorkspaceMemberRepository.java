package com.sso.repository;

import com.sso.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** Is this user a member of ANY workspace under this org? Used by OrgAccessFilter
     *  so workspace-only members (no direct users.organization_id row for this org)
     *  aren't wrongly 403'd on /api/orgs/{slug}/** endpoints. */
    boolean existsByUser_EmailAndWorkspace_Organization_Id(String email, UUID orgId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    void deleteByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * All workspaces a user is a member of — used for the workspace switcher,
     * and as SSOTokenCustomizer's fallback when a login doesn't specify which
     * workspace to mint a token for. Ordered by joinedAt ASC (their earliest/
     * "home" membership first) so that fallback is deterministic rather than
     * arbitrary DB-order: a user who belongs to more than one workspace used
     * to get whichever workspace happened to sort first at the JDBC/DB layer
     * (unspecified, could differ run to run), so their baked-in `permissions`
     * claim could silently come from the wrong workspace and drop things like
     * Chat's view_channels — not a full fix for genuine multi-workspace
     * access (that needs the workspace switcher to actually pass
     * workspace_id through login), but it stops the non-determinism.
     */
    @Query("""
            SELECT wm FROM WorkspaceMember wm
            JOIN FETCH wm.workspace w
            JOIN FETCH w.organization
            WHERE wm.user.id = :userId
              AND w.active = true
            ORDER BY wm.joinedAt ASC
            """)
    List<WorkspaceMember> findAllByUserIdWithWorkspace(@Param("userId") UUID userId);
}
