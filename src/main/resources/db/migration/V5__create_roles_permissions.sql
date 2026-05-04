-- Roles: can be org-level (client_id IS NULL) or app-specific (client_id IS NOT NULL)
CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id   VARCHAR(255) REFERENCES registered_clients (client_id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_roles_name_org_client UNIQUE (name, org_id, client_id)
);

-- Permissions: resource:action style, scoped to org
CREATE TABLE permissions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    resource    VARCHAR(100) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_permissions_name_org UNIQUE (name, org_id)
);

-- Many-to-many: role → permissions
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- User assigned a role within a specific app (client)
CREATE TABLE user_app_roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID         NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    org_id      UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id   VARCHAR(255) NOT NULL REFERENCES registered_clients (client_id) ON DELETE CASCADE,
    assigned_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_app_roles UNIQUE (user_id, role_id, client_id)
);

CREATE INDEX idx_user_app_roles_user_client ON user_app_roles (user_id, client_id);
