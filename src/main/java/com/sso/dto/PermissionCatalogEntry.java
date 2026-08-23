package com.sso.dto;

/** One entry in an app's declared permission catalog — see PermissionCatalogService. */
public record PermissionCatalogEntry(
        String name,
        String resource,
        String action,
        String description
) {}
