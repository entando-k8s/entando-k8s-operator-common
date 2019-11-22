package org.entando.kubernetes.controller;

import org.entando.kubernetes.model.plugin.Permission;

public interface SimpleKeycloakClient {

    void login(String address, String username, String password);

    void ensureRealm(String realm);

    void createPublicClient(String realm, String domain);

    String prepareClientAndReturnSecret(KeycloakClientConfig config);

    void updateClient(KeycloakClientConfig config);

    void assignRoleToClientServiceAccount(String realm, String serviceAccountClientId, Permission serviceRole);
}
