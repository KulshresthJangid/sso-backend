package com.sso.controller;

import com.sso.dto.ClientResponse;
import com.sso.dto.CreateClientRequest;
import com.sso.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orgs/{slug}/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse register(@PathVariable String slug,
                                   @RequestBody CreateClientRequest req) {
        return clientService.register(slug, req);
    }

    @GetMapping
    public List<ClientResponse> list(@PathVariable String slug) {
        return clientService.listByOrg(slug);
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String slug, @PathVariable String clientId) {
        clientService.delete(slug, clientId);
    }
}
