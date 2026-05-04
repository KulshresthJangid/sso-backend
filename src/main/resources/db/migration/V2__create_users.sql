CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    org_role      VARCHAR(50)  NOT NULL DEFAULT 'ORG_MEMBER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email_org UNIQUE (email, org_id)
);

CREATE INDEX idx_users_org_id ON users (org_id);
CREATE INDEX idx_users_email   ON users (email);
