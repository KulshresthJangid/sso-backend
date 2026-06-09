package com.sso.config;

import com.sso.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Redirects unauthenticated users to /{slug}/login so TenantResolutionFilter
 * can set TenantContext before UsernamePasswordAuthenticationFilter runs.
 * Falls back to /login for requests without a tenant context.
 */
public class TenantAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        var org = TenantContext.get();
        String loginUrl = (org != null) ? "/" + org.getSlug() + "/login" : "/login";
        response.sendRedirect(loginUrl);
    }
}
