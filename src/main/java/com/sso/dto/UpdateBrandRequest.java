package com.sso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Slug is deliberately absent — it's the brand's URL/tenant identifier
// (OAuth2 clients, orgs, users, the JWT issuer all key off it), so it isn't
// editable after onboarding. Every other field can be changed any time from
// the platform console's Edit modal.
public record UpdateBrandRequest(
        @NotBlank(message = "Name is required")
        String name,

        String logoUrl,
        String primaryColor,
        String secondaryColor,

        @Pattern(regexp = "^(MINIMAL|AURORA|MIDNIGHT|BENTO)$", message = "Unknown landing template")
        String landingTemplate,

        @Pattern(regexp = "^(MINIMAL|AURORA|MIDNIGHT|BENTO)$", message = "Unknown dashboard template")
        String dashboardTemplate,

        @Pattern(regexp = "^(INTER|OUTFIT|SPACE_GROTESK|DM_SANS|POPPINS|OPEN_SANS|INSTRUMENT_SERIF)$", message = "Unknown landing font")
        String landingFont,

        @Pattern(regexp = "^(INTER|OUTFIT|SPACE_GROTESK|DM_SANS|POPPINS|OPEN_SANS|INSTRUMENT_SERIF)$", message = "Unknown dashboard font")
        String dashboardFont
) {}
