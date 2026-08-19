-- Fold the existing 'kaizex' org into the new Brand tier. Purely additive —
-- the org's slug, workspace, users, roles, and registered client are
-- untouched. Login URL segment stays "kaizex" either way (brand slug ==
-- org slug today by coincidence), so this is invisible at the URL level.
INSERT INTO brands (slug, name)
SELECT 'kaizex', 'Kaizex'
WHERE EXISTS (SELECT 1 FROM organizations WHERE slug = 'kaizex')
  AND NOT EXISTS (SELECT 1 FROM brands WHERE slug = 'kaizex');

UPDATE organizations
SET brand_id = (SELECT id FROM brands WHERE slug = 'kaizex')
WHERE slug = 'kaizex' AND brand_id IS NULL;

UPDATE registered_clients
SET brand_id = (SELECT id FROM brands WHERE slug = 'kaizex')
WHERE org_id = (SELECT id FROM organizations WHERE slug = 'kaizex')
  AND brand_id IS NULL;
