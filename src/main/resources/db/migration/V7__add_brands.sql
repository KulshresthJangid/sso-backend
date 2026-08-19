-- Brands: white-label resellers sitting above Organization. A brand owns
-- exactly one OAuth2 client and is the URL/login tenant unit going forward
-- (/{brandSlug}/oauth2/authorize) — Organization keeps its own slug for
-- existing admin-API addressing but becomes "a customer of a brand."
CREATE TABLE brands (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    logo_url        TEXT,
    primary_color   VARCHAR(20),
    secondary_color VARCHAR(20),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_brands_slug ON brands (slug);

ALTER TABLE organizations
    ADD COLUMN brand_id UUID REFERENCES brands (id);

CREATE INDEX idx_organizations_brand ON organizations (brand_id);

-- registered_clients can now be owned by a brand instead of a single org
-- (a brand-level client is shared by every org under that brand's login
-- flow), so org_id becomes optional going forward. Existing rows keep their
-- org_id untouched; the Kaizex data migration backfills brand_id alongside it.
ALTER TABLE registered_clients
    ADD COLUMN brand_id UUID REFERENCES brands (id),
    ALTER COLUMN org_id DROP NOT NULL;

CREATE INDEX idx_registered_clients_brand_id ON registered_clients (brand_id);
