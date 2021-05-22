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

package org.entando.kubernetes.controller.spi.capability;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public interface CapabilityProvider {

    CapabilityProvisioningResult provideCapability(EntandoCustomResource forResource, CapabilityRequirement capabilityRequirement,
            int timeoutSeconds) throws TimeoutException;

    CapabilityProvisioningResult loadProvisioningResult(ProvidedCapability providedCapability);
}
