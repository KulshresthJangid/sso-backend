package com.sso.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Provides the JWK signing key. The RSA private key is stored as a PKCS8 PEM file
 * so the same kid is reused across SSO restarts. If the file does not exist it is
 * generated on first startup.
 *
 * Files written:
 *   ${app.key-store-path}.key  — Base64-encoded PKCS8 private key bytes
 *   ${app.key-store-path}.kid  — UUID key ID
 */
@Configuration
public class KeyConfig {

    private static final Logger log = LoggerFactory.getLogger(KeyConfig.class);

    @Value("${app.key-store-path:./sso-signing}")
    private String keyStorePath;

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        Path keyFile = Path.of(keyStorePath + ".key");
        Path kidFile = Path.of(keyStorePath + ".kid");

        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        String kid;

        if (keyFile.toFile().exists() && kidFile.toFile().exists()) {
            byte[] keyBytes = Base64.getDecoder().decode(Files.readString(keyFile).strip());
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
            // Reconstruct public key from private key
            java.security.interfaces.RSAPrivateCrtKey crtKey = (java.security.interfaces.RSAPrivateCrtKey) privateKey;
            var pubSpec = new java.security.spec.RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
            publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(pubSpec);
            kid = Files.readString(kidFile).strip();
            log.info("Loaded persistent signing key from {} (kid={})", keyFile, kid);
        } else {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            privateKey = (RSAPrivateKey) kp.getPrivate();
            publicKey = (RSAPublicKey) kp.getPublic();
            kid = UUID.randomUUID().toString();

            Files.writeString(keyFile, Base64.getEncoder().encodeToString(privateKey.getEncoded()));
            Files.writeString(kidFile, kid);
            log.info("Generated new persistent signing key at {} (kid={})", keyFile, kid);
        }

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(kid)
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }
}
