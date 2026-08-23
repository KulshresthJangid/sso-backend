-- Which landing page / dashboard visual template a brand picked during
-- onboarding (see BrandsPage.tsx's wizard). Plain strings rather than a DB
-- enum so a 5th template can be added later without another migration —
-- validated against the known set at the DTO level instead
-- (CreateBrandRequest's @Pattern).
ALTER TABLE brands
    ADD COLUMN landing_template   VARCHAR(20) NOT NULL DEFAULT 'MINIMAL',
    ADD COLUMN dashboard_template VARCHAR(20) NOT NULL DEFAULT 'MINIMAL';
