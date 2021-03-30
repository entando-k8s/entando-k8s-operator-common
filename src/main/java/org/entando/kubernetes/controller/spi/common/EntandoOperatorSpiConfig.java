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

import java.util.Optional;

public class EntandoOperatorSpiConfig {

    private EntandoOperatorSpiConfig() {
    }

    public static boolean assumeExternalHttpsProvider() {
        return EntandoOperatorConfigBase.lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER)
                .map(Boolean::valueOf).orElse(false);
    }

    public static EntandoOperatorComplianceMode getComplianceMode() {
        return EntandoOperatorConfigBase.lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE)
                .map(EntandoOperatorComplianceMode::resolve).orElse(EntandoOperatorComplianceMode.COMMUNITY);
    }

    public static boolean forceExternalAccessToKeycloak() {
        return EntandoOperatorConfigBase.lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_FORCE_EXTERNAL_ACCESS_TO_KEYCLOAK)
                .map(Boolean::valueOf).orElse(false);
    }

    public static Optional<String> getDefaultClusteredStorageClass() {
        return EntandoOperatorConfigBase
                .lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_DEFAULT_CLUSTERED_STORAGE_CLASS);
    }

    public static Optional<String> getPvcAccessModeOverride() {
        return EntandoOperatorConfigBase.lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_PVC_ACCESSMODE_OVERRIDE);
    }

    public static Optional<String> getDefaultNonClusteredStorageClass() {
        return EntandoOperatorConfigBase
                .lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_DEFAULT_NON_CLUSTERED_STORAGE_CLASS);
    }

}
