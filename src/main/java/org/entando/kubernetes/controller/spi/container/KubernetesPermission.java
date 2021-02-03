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

package org.entando.kubernetes.controller.spi.container;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Arrays;
import java.util.List;

@JsonSerialize
@JsonDeserialize
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
public class KubernetesPermission {

    private final String apiGroup;
    private final String resourceName;
    private final List<String> verbs;

    public KubernetesPermission(String apiGroup, String resourceName, String... verbs) {
        this(apiGroup, resourceName, Arrays.asList(verbs));
    }

    @JsonCreator
    public KubernetesPermission(
            @JsonProperty("apiGroup") String apiGroup,
            @JsonProperty("resourceName") String resourceName,
            @JsonProperty("verbs") List<String> verbs) {
        this.apiGroup = apiGroup;
        this.resourceName = resourceName;
        this.verbs = verbs;

    }

    public String getApiGroup() {
        return apiGroup;
    }

    public String getResourceName() {
        return resourceName;
    }

    public List<String> getVerbs() {
        return verbs;
    }
}
