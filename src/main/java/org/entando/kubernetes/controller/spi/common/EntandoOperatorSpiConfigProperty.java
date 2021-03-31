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

package org.entando.kubernetes.controller.spi.common;

public enum EntandoOperatorSpiConfigProperty implements ConfigProperty {

    ENTANDO_K8S_OPERATOR_DEFAULT_CLUSTERED_STORAGE_CLASS("entando.k8s.operator.default.clustered.storage.class"),
    ENTANDO_K8S_OPERATOR_DEFAULT_NON_CLUSTERED_STORAGE_CLASS("entando.k8s.operator.default.non.clustered.storage.class"),
    ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE("entando.k8s.operator.compliance.mode"),
    ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER("entando.assume.external.https.provider"),
    ENTANDO_FORCE_EXTERNAL_ACCESS_TO_KEYCLOAK("entando.force.external.access.to.keycloak"),
    ENTANDO_K8S_OPERATOR_PVC_ACCESSMODE_OVERRIDE("entando.k8s.operator.pvc.accessmode.override");

    private final String jvmSystemProperty;

    EntandoOperatorSpiConfigProperty(String jvmSystemProperty) {
        this.jvmSystemProperty = jvmSystemProperty;
    }

    public String getJvmSystemProperty() {
        return jvmSystemProperty;
    }
}
