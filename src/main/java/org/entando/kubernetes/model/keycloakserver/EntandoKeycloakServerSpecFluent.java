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

package org.entando.kubernetes.model.keycloakserver;

import org.entando.kubernetes.model.EntandoIngressingDeploymentSpecFluent;

public class EntandoKeycloakServerSpecFluent<N extends EntandoKeycloakServerSpecFluent<N>>
        extends EntandoIngressingDeploymentSpecFluent<N> {

    protected String customImage;
    protected boolean isDefault;
    private StandardKeycloakImage standardImage;
    private String frontEndUrl;

    public EntandoKeycloakServerSpecFluent(EntandoKeycloakServerSpec spec) {
        super(spec);
        this.isDefault = spec.isDefault();
        this.customImage = spec.getCustomImage().orElse(null);
        this.standardImage = spec.getStandardImage().orElse(null);
        this.frontEndUrl = spec.getFrontEndUrl().orElse(null);
    }

    public EntandoKeycloakServerSpecFluent() {

    }

    public N withDefault(boolean isDefault) {
        this.isDefault = isDefault;
        return thisAsF();
    }

    public N withCustomImage(String customImage) {
        this.customImage = customImage;
        return thisAsF();
    }

    public N withFrontEndUrl(String frontEndUrl) {
        this.frontEndUrl = frontEndUrl;
        return thisAsF();
    }

    public N withStandardImage(StandardKeycloakImage standardImage) {
        this.standardImage = standardImage;
        return thisAsF();
    }

    public EntandoKeycloakServerSpec build() {
        return new EntandoKeycloakServerSpec(customImage, standardImage, frontEndUrl, dbms, ingressHostName, tlsSecretName, replicas,
                isDefault,
                serviceAccountToUse, environmentVariables, resourceRequirements);
    }
}
