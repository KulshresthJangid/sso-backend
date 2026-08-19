package com.sso.controller;

import com.sso.dto.CreateOrgRequest;
import com.sso.dto.CreateUserRequest;
import com.sso.dto.UserResponse;
import com.sso.exception.SSOException;
import com.sso.service.OrganizationService;
import com.sso.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {

    private final OrganizationService orgService;
    private final UserService userService;

    /** brandSlug is optional — omit for a legacy/platform-direct org with no reseller. */
    record SignupRequest(String orgName, String slug, String adminEmail, String adminPassword, String brandSlug) {}
    record SignupResponse(String slug, String orgName) {}
    record ClaimRequest(String slug, String adminEmail, String adminPassword) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SignupResponse signup(@RequestBody SignupRequest req) {
        orgService.create(new CreateOrgRequest(req.orgName(), req.slug(), req.brandSlug()));
        userService.create(req.slug(), new CreateUserRequest(req.adminEmail(), req.adminPassword(), "ORG_ADMIN"));
        return new SignupResponse(req.slug(), req.orgName());
    }

    /** Adds the first admin to an existing org that has no users yet. */
    @PostMapping("/claim")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserResponse claimOrg(@RequestBody ClaimRequest req) {
        if (!userService.listByOrg(req.slug()).isEmpty()) {
            throw SSOException.conflict("This organization already has users.");
        }
        var user = userService.create(req.slug(),
                new CreateUserRequest(req.adminEmail(), req.adminPassword(), "ORG_ADMIN"));
        return new UserResponse(user.getId().toString(), user.getEmail(),
                user.getOrgRole().name(), user.isActive());
    }
}
