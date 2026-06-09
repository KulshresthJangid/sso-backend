-- Add plan tier and platform flag to organizations
ALTER TABLE organizations
    ADD COLUMN plan        VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    ADD COLUMN is_platform BOOLEAN      NOT NULL DEFAULT FALSE;

-- Workspaces: belong to an org, named context for teams/projects
CREATE TABLE workspaces (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workspace_slug_org UNIQUE (slug, org_id)
);

CREATE INDEX idx_workspaces_org ON workspaces (org_id);

-- Workspace members: users (from any org) invited into a workspace
CREATE TABLE workspace_members (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID      NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workspace_member UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_user ON workspace_members (user_id);

-- Workspace-scoped role assignments (replaces user_app_roles for user flows)
CREATE TABLE user_workspace_roles (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id      UUID         NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    client_id    VARCHAR(255) NOT NULL REFERENCES registered_clients (client_id) ON DELETE CASCADE,
    assigned_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_workspace_role UNIQUE (workspace_id, user_id, role_id, client_id)
);

CREATE INDEX idx_uwr_workspace_user_client ON user_workspace_roles (workspace_id, user_id, client_id);

-- Allow roles to be scoped to a specific workspace (nullable = org-level role)
ALTER TABLE roles
    ADD COLUMN workspace_id UUID REFERENCES workspaces (id) ON DELETE CASCADE;
