package com.sso.controller;

import com.sso.dto.WorkspaceResponse;
import com.sso.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User-facing endpoints for the currently authenticated SSO principal.
 * Requires a valid Bearer JWT issued by this SSO.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final WorkspaceService workspaceService;

    /**
     * Returns all workspaces the calling user belongs to.
     * The Lumen frontend calls this to build the workspace switcher dropdown.
     */
    @GetMapping("/workspaces")
    public List<WorkspaceResponse> myWorkspaces(@AuthenticationPrincipal Jwt jwt) {
        // user_id is injected into the JWT by SSOTokenCustomizer
        String userIdStr = jwt.getClaimAsString("user_id");
        UUID userId = UUID.fromString(userIdStr);
        return workspaceService.getMyWorkspaces(userId);
    }
}
