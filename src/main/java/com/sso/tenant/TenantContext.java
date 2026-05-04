package com.sso.tenant;

import com.sso.entity.Organization;

/**
 * ThreadLocal holder for the current request's tenant (Organization).
 * Set by TenantResolutionFilter at the start of every tenant-scoped request,
 * and cleared in the finally block after the response is sent.
 */
public class TenantContext {

    private static final ThreadLocal<Organization> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Organization org) {
        CURRENT.set(org);
    }

    public static Organization get() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
