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
 * Spring Security's UserDetailsService — looks up users scoped to the current
 * tenant (brand). TenantContext must be set before this is called (done by
 * TenantResolutionFilter).
 *
 * The brand is known at this point (from the URL), but not which of the
 * brand's orgs the user belongs to — so this searches by email across every
 * org under the brand. See UserRepository.findFirstByEmailAndOrganization_Brand_IdAndActiveTrue
 * for the known limitation (assumes brand-scoped email uniqueness).
 */
@Service
@RequiredArgsConstructor
public class TenantAwareUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var brand = TenantContext.get();
        if (brand == null) {
            throw new UsernameNotFoundException("No tenant context — cannot authenticate user");
        }

        var user = userRepo.findFirstByEmailAndOrganization_Brand_IdAndActiveTrue(email, brand.getId())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email + " in brand: " + brand.getSlug()));

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
