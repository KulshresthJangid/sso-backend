package com.sso.config;

import com.sso.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * A failed login (bad credentials) needs to redirect back to the SAME
 * tenant's login page, /{slug}/login?error — not the bare /login?error
 * Spring's default SimpleUrlAuthenticationFailureHandler builds from a
 * hardcoded loginPage("/login"). Bare /login has no tenant segment for
 * nginx's tenant-regex block to match, so it falls through to whatever the
 * domain root otherwise serves instead of reaching this backend at all.
 *
 * Mirrors TenantAwareAuthenticationEntryPoint's slug lookup — same
 * TenantContext, same fallback to bare /login when it's unset.
 */
public class TenantAwareAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        var org = TenantContext.get();
        String loginUrl = (org != null) ? "/" + org.getSlug() + "/login?error" : "/login?error";
        response.sendRedirect(loginUrl);
    }
}
