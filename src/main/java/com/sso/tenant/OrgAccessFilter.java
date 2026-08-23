package com.sso.tenant;

import com.sso.entity.Organization;
import com.sso.entity.User;
import com.sso.repository.OrganizationRepository;
import com.sso.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every /api/orgs/{slug}/** endpoint (users, roles, permissions, clients)
 * only ever checked "is there a valid session at all" — never "does this
 * session actually have any relationship to *this* org." Any logged-in
 * user — an admin of a totally different org, or even just an ORG_MEMBER
 * with no admin rights anywhere — could hit another org's user/role/client
 * endpoints just by knowing its slug.
 *
 * This runs early in defaultFilterChain and enforces: the authenticated
 * principal must either (a) belong to the target org directly (any
 * OrgRole), or (b) be the SUPER_ADMIN of the brand that org belongs to
 * (see Brand Console — a brand's super admin manages every org under it).
 * Unauthenticated requests are left alone — permitAll routes like
 * GET /api/orgs/{slug} (slug-availability lookup, used unauthenticated by
 * signup) and POST /api/orgs (signup itself, no slug segment to match)
 * still work exactly as before; this only adds a check on top of requests
 * that already carry a real session.
 */
@Component
@RequiredArgsConstructor
public class OrgAccessFilter extends OncePerRequestFilter {

    private static final Pattern ORG_SCOPED_PATH = Pattern.compile("^/api/orgs/([^/]+)(/.*)?$");

    private final OrganizationRepository orgRepo;
    private final UserRepository userRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Matcher matcher = ORG_SCOPED_PATH.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            // No real session — permitAll routes (org lookup, signup) are
            // governed by defaultFilterChain's own rules downstream, same
            // as before this filter existed.
            chain.doFilter(request, response);
            return;
        }

        String slug = matcher.group(1);
        Optional<Organization> orgOpt = orgRepo.findBySlug(slug);
        if (orgOpt.isEmpty()) {
            // Let the 404 happen naturally further down the chain instead
            // of masking it as a 403.
            chain.doFilter(request, response);
            return;
        }

        Organization org = orgOpt.get();
        String email = auth.getName();

        boolean isOrgMember = userRepo.existsByEmailAndOrganizationId(email, org.getId());
        boolean isBrandSuperAdmin = org.getBrand() != null
                && userRepo.findFirstByEmailAndBrand_IdAndOrgRoleAndActiveTrue(email, org.getBrand().getId(), User.OrgRole.SUPER_ADMIN).isPresent();

        if (!isOrgMember && !isBrandSuperAdmin) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You don't have access to this organization");
            return;
        }

        chain.doFilter(request, response);
    }
}
