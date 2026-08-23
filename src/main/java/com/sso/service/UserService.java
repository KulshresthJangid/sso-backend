package com.sso.service;

import com.sso.dto.CreateSuperAdminRequest;
import com.sso.dto.CreateUserRequest;
import com.sso.entity.Brand;
import com.sso.entity.Organization;
import com.sso.entity.User;
import com.sso.entity.Workspace;
import com.sso.entity.WorkspaceMember;
import com.sso.exception.SSOException;
import com.sso.repository.UserRepository;
import com.sso.repository.WorkspaceMemberRepository;
import com.sso.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final OrganizationService orgService;
    private final BrandService brandService;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository workspaceMemberRepo;

    @Transactional
    public User create(String orgSlug, CreateUserRequest req) {
        Organization org = orgService.getBySlug(orgSlug);

        if (userRepo.existsByEmailAndOrganizationId(req.email(), org.getId())) {
            throw SSOException.conflict("Email already registered in this org: " + req.email());
        }

        User.OrgRole role = User.OrgRole.ORG_MEMBER;
        if (req.orgRole() != null) {
            try { role = User.OrgRole.valueOf(req.orgRole()); }
            catch (IllegalArgumentException e) { throw SSOException.badRequest("Invalid org role: " + req.orgRole()); }
        }

        User saved = userRepo.save(User.builder()
                .organization(org)
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .orgRole(role)
                .build());

        // Org creation (BrandConsoleController, SignupController,
        // OrganizationController) never provisioned a Workspace — every org
        // signed up self-service ended up with zero workspaces, and
        // SSOTokenCustomizer/WorkspaceService.assignRole both hard-require
        // one ("every org has exactly one real workspace today"). No
        // workspace meant no role could ever be assigned to anyone in that
        // org, which meant every JWT minted permissions:[] regardless of
        // what roles existed. Bootstrapping it here — the one place every
        // org-creation path already converges on — covers all of them at
        // once instead of duplicating this in each controller.
        Workspace workspace = workspaceRepo.findAllByOrganizationIdAndActiveTrue(org.getId())
                .stream()
                .findFirst()
                .orElseGet(() -> workspaceRepo.save(Workspace.builder()
                        .organization(org)
                        .name("Default")
                        .slug("default")
                        .build()));
        workspaceMemberRepo.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(saved)
                .build());

        return saved;
    }

    /**
     * Creates the one SUPER_ADMIN account for a brand — not scoped to any
     * org, sees every org under the brand (see SSOTokenCustomizer's
     * brand_workspace_ids claim). Called from the onboarding wizard, gated
     * by the platform-admin filter chain same as brand creation itself.
     */
    @Transactional
    public User createSuperAdmin(String brandSlug, CreateSuperAdminRequest req) {
        Brand brand = brandService.getBySlug(brandSlug);

        if (userRepo.existsByBrand_IdAndOrgRole(brand.getId(), User.OrgRole.SUPER_ADMIN)) {
            throw SSOException.conflict("This brand already has a super admin.");
        }

        return userRepo.save(User.builder()
                .brand(brand)
                .organization(null)
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .orgRole(User.OrgRole.SUPER_ADMIN)
                .build());
    }

    public List<User> listByOrg(String orgSlug) {
        Organization org = orgService.getBySlug(orgSlug);
        return userRepo.findAllByOrganizationId(org.getId());
    }

    @Transactional
    public void deactivate(String orgSlug, UUID userId) {
        Organization org = orgService.getBySlug(orgSlug);
        User user = userRepo.findById(userId)
                .filter(u -> u.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> SSOException.notFound("User not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && user.getEmail().equals(auth.getName())) {
            throw SSOException.badRequest("You cannot deactivate your own account.");
        }

        user.setActive(false);
        userRepo.save(user);
    }
}
