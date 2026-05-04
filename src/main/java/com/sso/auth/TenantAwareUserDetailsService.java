package com.sso.auth;

import com.sso.repository.UserRepository;
import com.sso.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security's UserDetailsService — looks up users scoped to the current tenant.
 * TenantContext must be set before this is called (done by TenantResolutionFilter).
 */
@Service
@RequiredArgsConstructor
public class TenantAwareUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var organization = TenantContext.get();
        if (organization == null) {
            throw new UsernameNotFoundException("No tenant context — cannot authenticate user");
        }

        var user = userRepo.findByEmailAndOrganizationIdAndActiveTrue(email, organization.getId())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email + " in org: " + organization.getSlug()));

        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getOrgRole().name())
                ))
                .accountLocked(!user.isActive())
                .build();
    }
}
