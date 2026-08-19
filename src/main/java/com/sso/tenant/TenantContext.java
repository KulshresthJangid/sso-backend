package com.sso.tenant;

import com.sso.entity.Brand;

/**
 * ThreadLocal holder for the current request's tenant (Brand — the OAuth2/
 * login URL unit, e.g. /{brandSlug}/oauth2/authorize). Set by
 * TenantResolutionFilter at the start of every tenant-scoped request, and
 * cleared in the finally block after the response is sent.
 *
 * Note: this used to hold Organization directly, back when Organization was
 * the URL tenant unit. Brand now owns that role; Organization is "a customer
 * of a brand" and gets resolved separately, from the authenticated user, once
 * one is known (see TenantAwareUserDetailsService / SSOTokenCustomizer).
 */
public class TenantContext {

    private static final ThreadLocal<Brand> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Brand brand) {
        CURRENT.set(brand);
    }

    public static Brand get() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
