package com.sso.tenant;

import com.sso.repository.OrganizationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Runs before every request.
 * 1. Extracts the tenant slug from the URL: /acme-corp/oauth2/authorize → "acme-corp"
 * 2. Loads the Organization from DB and sets TenantContext
 * 3. Wraps the request to strip the slug from the path so Spring Auth Server
 *    sees a standard path: /oauth2/authorize
 * 4. Clears TenantContext in the finally block
 */
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final OrganizationRepository orgRepo;

    // These paths are NOT tenant-scoped — skip slug extraction
    private static final Set<String> NON_TENANT_PREFIXES = Set.of(
            "/api/", "/error", "/actuator/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();

            if (isNonTenantPath(path)) {
                chain.doFilter(request, response);
                return;
            }

            String slug = extractSlug(path);
            if (slug == null || slug.isBlank()) {
                chain.doFilter(request, response);
                return;
            }

            var org = orgRepo.findBySlugAndActiveTrue(slug);
            if (org.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Organization not found: " + slug);
                return;
            }

            TenantContext.set(org.get());
            // Strip slug prefix so downstream sees /oauth2/... not /acme-corp/oauth2/...
            chain.doFilter(new SlugStrippedRequest(request, slug), response);

        } finally {
            TenantContext.clear();
        }
    }

    private boolean isNonTenantPath(String path) {
        return NON_TENANT_PREFIXES.stream().anyMatch(path::startsWith) || path.equals("/");
    }

    private String extractSlug(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.isEmpty()) return null;
        int idx = p.indexOf('/');
        return idx == -1 ? p : p.substring(0, idx);
    }

    /** Strips /{slug} from URI so the auth server sees standard paths. */
    private static class SlugStrippedRequest extends HttpServletRequestWrapper {
        private final String prefix;

        SlugStrippedRequest(HttpServletRequest req, String slug) {
            super(req);
            this.prefix = "/" + slug;
        }

        @Override
        public String getRequestURI() {
            String uri = super.getRequestURI();
            return uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri;
        }

        @Override
        public String getServletPath() {
            String path = super.getServletPath();
            return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
        }
    }
}
