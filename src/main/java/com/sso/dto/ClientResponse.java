package com.sso.dto;

import java.util.List;

/** Returned on client creation — clientSecret is only shown once. */
public record ClientResponse(
        String id,
        String clientId,
        String clientSecret,   // plaintext, only non-null on creation
        String clientName,
        String orgId,
        List<String> redirectUris,
        List<String> scopes,
        List<String> grantTypes
) {}
