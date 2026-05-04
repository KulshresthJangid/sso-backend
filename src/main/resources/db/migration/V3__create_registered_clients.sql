-- Custom registered_clients table (extends Spring Authorization Server schema with org_id)
CREATE TABLE registered_clients (
    id                              VARCHAR(255) PRIMARY KEY,
    org_id                          UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id                       VARCHAR(255) NOT NULL UNIQUE,
    client_id_issued_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    client_secret                   VARCHAR(255),
    client_secret_expires_at        TIMESTAMP,
    client_name                     VARCHAR(255) NOT NULL,
    client_authentication_methods   TEXT         NOT NULL,
    authorization_grant_types       TEXT         NOT NULL,
    redirect_uris                   TEXT,
    post_logout_redirect_uris       TEXT,
    scopes                          TEXT         NOT NULL,
    client_settings                 TEXT         NOT NULL,
    token_settings                  TEXT         NOT NULL,
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_registered_clients_org_id    ON registered_clients (org_id);
CREATE INDEX idx_registered_clients_client_id ON registered_clients (client_id);
