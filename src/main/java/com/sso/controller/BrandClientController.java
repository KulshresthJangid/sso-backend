package com.sso.controller;

import com.sso.dto.ClientResponse;
import com.sso.dto.CreateClientRequest;
import com.sso.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OAuth2 client registration for brand-owned clients — the path new brands
 * (Zoralis, etc.) use. Mirrors ClientController's org-scoped endpoints
 * exactly; see ClientService.registerForBrand for why a brand's client is a
 * single shared client across all its orgs rather than one per org.
 */
@RestController
@RequestMapping("/api/brands/{slug}/clients")
@RequiredArgsConstructor
public class BrandClientController {

    private final ClientService clientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse register(@PathVariable String slug,
                                   @RequestBody CreateClientRequest req) {
        return clientService.registerForBrand(slug, req);
    }

    @GetMapping
    public List<ClientResponse> list(@PathVariable String slug) {
        return clientService.listByBrand(slug);
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String slug, @PathVariable String clientId) {
        clientService.deleteForBrand(slug, clientId);
    }
}
