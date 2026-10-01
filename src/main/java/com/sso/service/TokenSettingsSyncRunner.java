package com.sso.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Brings every already-registered OAuth client's tokenSettings in line with
 * ClientService's current ACCESS_TOKEN_TTL/REFRESH_TOKEN_TTL policy — see
 * ClientService.syncAllTokenSettings for why this can't just be a code
 * constant change (tokenSettings is serialized once into the DB at client
 * creation, never recomputed). Idempotent: a no-op once every client
 * already matches, so running it on every startup is harmless and means
 * the next policy change (if any) rolls out to existing clients the same
 * way, not just new ones.
 */
@Component
@RequiredArgsConstructor
public class TokenSettingsSyncRunner implements CommandLineRunner {

    private final ClientService clientService;

    @Override
    public void run(String... args) {
        clientService.syncAllTokenSettings();
    }
}
