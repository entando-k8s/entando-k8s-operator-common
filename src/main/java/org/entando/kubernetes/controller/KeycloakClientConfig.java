package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.model.plugin.ExpectedRole;
import org.entando.kubernetes.model.plugin.Permission;

public class KeycloakClientConfig {

    private final String realm;
    private final String clientId;
    private final String clientName;
    private final List<ExpectedRole> roles;
    private final List<Permission> permissions;
    private List<String> redirectUri = new ArrayList<>();

    public KeycloakClientConfig(String realm, String clientId, String clientName, List<ExpectedRole> roles,
            List<Permission> permissions) {
        this.realm = realm;
        this.clientId = clientId;
        this.clientName = clientName;
        this.roles = ofNullable(roles).map(ArrayList::new).orElseGet(ArrayList::new);
        this.permissions = ofNullable(permissions).map(ArrayList::new).orElseGet(ArrayList::new);
    }

    public KeycloakClientConfig(String realm, String clientId, String clientName) {
        this(realm, clientId, clientName, null, null);
    }

    public KeycloakClientConfig withRedirectUri(String redirectUri) {
        this.redirectUri.add(redirectUri);
        return this;
    }

    public List<ExpectedRole> getRoles() {
        return roles;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getRealm() {
        return realm;
    }

    public KeycloakClientConfig withPermission(String clientId, String role) {
        permissions.add(new Permission(clientId, role));
        return this;
    }

    public KeycloakClientConfig withRole(String role) {
        roles.add(new ExpectedRole(role));
        return this;
    }

    public List<String> getRedirectUris() {
        return redirectUri;
    }
}
