package com.sso.controller;

import com.sso.dto.CreateOrgRequest;
import com.sso.entity.Organization;
import com.sso.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService orgService;

    /** Public signup endpoint */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateOrgRequest req) {
        Organization org = orgService.create(req);
        return Map.of("id", org.getId(), "name", org.getName(), "slug", org.getSlug());
    }

    @GetMapping("/{slug}")
    public Map<String, Object> get(@PathVariable String slug) {
        Organization org = orgService.getBySlug(slug);
        return Map.of("id", org.getId(), "name", org.getName(),
                "slug", org.getSlug(), "active", org.isActive());
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String slug) {
        orgService.deactivate(slug);
    }
}
