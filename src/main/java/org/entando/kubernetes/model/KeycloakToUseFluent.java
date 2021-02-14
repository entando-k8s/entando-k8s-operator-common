/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.model;

public class KeycloakToUseFluent<N extends KeycloakToUseFluent> {

    private String namespace;
    private String name;
    private String realm;
    private String publicClientId;

    public KeycloakToUseFluent(KeycloakToUse keycloakToUse) {
        this.namespace = keycloakToUse.getNamespace().orElse(null);
        this.name = keycloakToUse.getName();
        this.realm = keycloakToUse.getRealm().orElse(null);
        this.publicClientId = keycloakToUse.getPublicClientId().orElse(null);
    }

    public KeycloakToUseFluent() {
    }

    public KeycloakToUse build() {
        return new KeycloakToUse(namespace, name, realm, publicClientId);
    }

    public N withNamespace(String namespace) {
        this.namespace = namespace;
        return thisAsN();
    }

    public N withName(String name) {
        this.name = name;
        return thisAsN();
    }

    public N withRealm(String realm) {
        this.realm = realm;
        return thisAsN();
    }

    public N withPublicClientId(String publicClientId) {
        this.publicClientId = publicClientId;
        return thisAsN();
    }

    @SuppressWarnings("unchecked")
    private N thisAsN() {
        return (N) this;
    }

}
