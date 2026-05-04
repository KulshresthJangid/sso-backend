package com.sso.dto;

import java.util.List;

public record CreateClientRequest(
        String clientName,
        List<String> redirectUris,
        List<String> scopes,       // defaults: openid, profile, email
        List<String> grantTypes    // defaults: authorization_code, refresh_token
) {}
