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

package org.entando.kubernetes.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.Map;
import java.util.Optional;

public class PropertyResolution {

    private final Optional<ConfigMap> imageVersionsConfigMap;
    private final String imagename;
    private EntandoOperatorConfigProperty overridingPropertyName;
    private String configMapKey;
    private EntandoOperatorConfigProperty defaultPropertyName;
    private String defaultValue;

    public PropertyResolution(ConfigMap imageVersionsConfigMap, String imagename) {
        this.imageVersionsConfigMap = Optional.ofNullable(imageVersionsConfigMap);
        this.imagename = imagename;
    }

    public PropertyResolution withOverridingPropertyName(EntandoOperatorConfigProperty overridingPropertyName) {
        this.overridingPropertyName = overridingPropertyName;
        return this;
    }

    public PropertyResolution withConfigMapKey(String configMapKey) {
        this.configMapKey = configMapKey;
        return this;
    }

    public PropertyResolution withDefaultPropertyName(EntandoOperatorConfigProperty defaultPropertyName) {
        this.defaultPropertyName = defaultPropertyName;
        return this;
    }

    public PropertyResolution withDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public String resolvePropertyValue() {
        return overridingPropertyValue().orElse(valueFromConfigMap().orElse(defaultPropertyValue().orElse(defaultValue)));
    }

    private Optional<String> defaultPropertyValue() {
        return EntandoOperatorConfigBase.lookupProperty(defaultPropertyName);
    }

    private Optional<String> overridingPropertyValue() {
        return EntandoOperatorConfigBase.lookupProperty(overridingPropertyName);
    }

    private Optional<String> valueFromConfigMap() {
        Optional<String> result = Optional.empty();
        if (this.imageVersionsConfigMap.isPresent()) {
            Optional<Map<String, String>> data = Optional.ofNullable(this.imageVersionsConfigMap.get().getData());
            if (data.isPresent()) {
                Optional<String> configMapContent = Optional.ofNullable(data.get().get(imagename));
                if (configMapContent.isPresent()) {
                    result = extractValueFromContent(configMapContent.get());
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractValueFromContent(String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> imageConfig = mapper.readValue(content, Map.class);
            return Optional.ofNullable(imageConfig.get(configMapKey));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

}
