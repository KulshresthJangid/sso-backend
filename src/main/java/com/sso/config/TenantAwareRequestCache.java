package com.sso.config;

import com.sso.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Fixes the post-login redirect losing the tenant slug.
 *
 * TenantResolutionFilter wraps every tenant-scoped request in a
 * SlugStrippedRequest so Spring AS sees a plain /oauth2/authorize instead of
 * /{slug}/oauth2/authorize. That wrapper only overrides getRequestURI()/
 * getServletPath() — it never touches getRequestURL(). Spring Security's
 * default HttpSessionRequestCache, however, builds the saved redirect URL
 * from DefaultSavedRequest's own requestURI field, which it populates by
 * calling request.getRequestURI() on whatever request it's handed — i.e. the
 * already-stripped one. So an unauthenticated hit on
 * /kaizex/oauth2/authorize gets saved as bare /oauth2/authorize, and after
 * login the user is bounced there instead — a path nginx's tenant-regex
 * block doesn't match (no slug segment), so it never reaches this backend.
 *
 * Re-prepend the slug (from TenantContext, still set at save time — it's
 * only cleared in TenantResolutionFilter's finally block, after the filter
 * chain below it returns) before delegating to the normal save logic.
 */
public class TenantAwareRequestCache extends HttpSessionRequestCache {

    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        var org = TenantContext.get();
        if (org == null) {
            super.saveRequest(request, response);
            return;
        }
        String prefix = "/" + org.getSlug();
        HttpServletRequest withSlug = new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                String uri = request.getRequestURI();
                return uri.startsWith(prefix) ? uri : prefix + uri;
            }
        };
        super.saveRequest(withSlug, response);
    }
}
