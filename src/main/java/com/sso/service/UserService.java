package com.sso.service;

import com.sso.dto.CreateUserRequest;
import com.sso.entity.Organization;
import com.sso.entity.User;
import com.sso.exception.SSOException;
import com.sso.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final OrganizationService orgService;
    private final PasswordEncoder passwordEncoder;

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

        return userRepo.save(User.builder()
                .organization(org)
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .orgRole(role)
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
        user.setActive(false);
        userRepo.save(user);
    }
}
