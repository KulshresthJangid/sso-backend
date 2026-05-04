package com.sso.repository;

import com.sso.entity.UserAppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserAppRoleRepository extends JpaRepository<UserAppRole, UUID> {

    List<UserAppRole> findAllByUserIdAndClientId(UUID userId, String clientId);

    List<UserAppRole> findAllByOrganizationId(UUID orgId);

    /**
     * Eagerly fetch roles + permissions for JWT customizer.
     * Used when building token claims for a specific user + app.
     */
    @Query("""
            SELECT uar FROM UserAppRole uar
            JOIN FETCH uar.role r
            JOIN FETCH r.permissions
            WHERE uar.user.id = :userId
              AND uar.clientId = :clientId
            """)
    List<UserAppRole> findWithPermissionsByUserIdAndClientId(
            @Param("userId") UUID userId,
            @Param("clientId") String clientId);

    void deleteByUserIdAndRoleIdAndClientId(UUID userId, UUID roleId, String clientId);
}
