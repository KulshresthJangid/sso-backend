-- A brand-level SUPER_ADMIN manages every org under one brand rather than
-- belonging to a single org — see User.java. org_id becomes optional for
-- exactly this case; brand_id is set only for it.
ALTER TABLE users
    ALTER COLUMN org_id DROP NOT NULL,
    ADD COLUMN brand_id UUID REFERENCES brands(id);

-- At most one SUPER_ADMIN per brand.
CREATE UNIQUE INDEX users_brand_super_admin_uk
    ON users (brand_id)
    WHERE org_role = 'SUPER_ADMIN';

-- Email uniqueness for brand-level admins — the existing (email, org_id)
-- constraint doesn't cover them since org_id is null (and Postgres treats
-- distinct NULLs as non-equal, so it wouldn't catch duplicates here anyway).
CREATE UNIQUE INDEX users_email_brand_super_admin_uk
    ON users (email, brand_id)
    WHERE org_id IS NULL;
