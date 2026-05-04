package com.sso.service;

import com.sso.dto.CreateOrgRequest;
import com.sso.entity.Organization;
import com.sso.exception.SSOException;
import com.sso.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository orgRepo;

    @Transactional
    public Organization create(CreateOrgRequest req) {
        if (orgRepo.existsBySlug(req.slug())) {
            throw SSOException.conflict("Slug already taken: " + req.slug());
        }
        return orgRepo.save(Organization.builder()
                .name(req.name())
                .slug(req.slug())
                .build());
    }

    public Organization getBySlug(String slug) {
        return orgRepo.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> SSOException.notFound("Organization not found: " + slug));
    }

    @Transactional
    public void deactivate(String slug) {
        Organization org = getBySlug(slug);
        org.setActive(false);
        orgRepo.save(org);
    }
}
