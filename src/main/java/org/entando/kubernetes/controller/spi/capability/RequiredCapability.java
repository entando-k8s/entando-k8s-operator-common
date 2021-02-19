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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.model.ResourceReference;

@JsonSerialize
@JsonDeserialize
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
public class RequiredCapability {

    private StandardCapability capability;
    private StandardCapabilityImplementation implementation;
    private CapabilityScope capabilityRequirementScope;
    private Map<String, String> additionalLabelsToMatch;
    private Map<String, String> capabilityParameters;
    private ResourceReference specifiedCapability;

    public RequiredCapability() {
    }

    @JsonCreator
    public RequiredCapability(@JsonProperty("capability") StandardCapability capability,
            @JsonProperty("implementation") StandardCapabilityImplementation implementation,
            @JsonProperty("capabilityRequirementScope") CapabilityScope capabilityRequirementScope,
            @JsonProperty("labelsToMatch") Map<String, String> labelsToMatch,
            @JsonProperty("capabilityParameters") Map<String, String> capabilityParameters,
            @JsonProperty("specifiedCapability") ResourceReference specifiedCapability) {
        this.capability = capability;
        this.implementation = implementation;
        this.capabilityRequirementScope = capabilityRequirementScope;
        this.additionalLabelsToMatch = labelsToMatch;
        this.capabilityParameters = capabilityParameters;
        this.specifiedCapability = specifiedCapability;
    }

    public StandardCapability getCapability() {
        return capability;
    }

    public Optional<StandardCapabilityImplementation> getImplementation() {
        return Optional.ofNullable(implementation);
    }

    public Optional<CapabilityScope> getCapabilityRequirementScope() {
        return Optional.ofNullable(capabilityRequirementScope);
    }

    public Map<String, String> getAdditionalLabelsToMatch() {
        return additionalLabelsToMatch;
    }

    public Map<String, String> getCapabilityParameters() {
        return capabilityParameters;
    }

    public Optional<ResourceReference> getSpecifiedCapability() {
        return Optional.ofNullable(specifiedCapability);
    }

    public Map<String, String> getCapabilityLabels() {
        Map<String, String> result = new HashMap<>();
        result.put(ProvidedCapability.CAPABILITY_LABEL_NAME, capability.getCamelCaseName());
        result.put(ProvidedCapability.IMPLEMENTATION_LABEL_NAME, implementation.getCamelCaseName());
        result.put(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME, capabilityRequirementScope.getCamelCaseName());
        return result;
    }
}
