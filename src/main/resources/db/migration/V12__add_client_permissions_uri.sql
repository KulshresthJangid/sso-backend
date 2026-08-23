-- Permission discovery contract: an app registering as an OAuth2 client can
-- optionally declare a URL SSO calls to fetch its full permission catalog
-- (see PermissionCatalogService). Nullable — apps that don't declare one
-- just don't get catalog-sync; orgs still fall back to typing permissions
-- by hand, same as before.
ALTER TABLE registered_clients
    ADD COLUMN permissions_uri TEXT;
