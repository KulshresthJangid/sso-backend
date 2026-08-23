-- Optional per-surface font override — independent of landing_template /
-- dashboard_template (a brand can keep e.g. the Aurora landing style but
-- swap its font). NULL means "use the template's own default font".
ALTER TABLE brands
    ADD COLUMN landing_font   VARCHAR(30),
    ADD COLUMN dashboard_font VARCHAR(30);
