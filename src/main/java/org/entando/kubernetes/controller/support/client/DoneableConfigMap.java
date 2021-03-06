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

package org.entando.kubernetes.controller.support.client;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapFluentImpl;
import java.util.function.UnaryOperator;

public class DoneableConfigMap extends ConfigMapFluentImpl<DoneableConfigMap> {

    private final UnaryOperator<ConfigMap> action;
    private final Object hashCode = new Object();

    public DoneableConfigMap(UnaryOperator<ConfigMap> action) {
        this.action = action;
    }

    public DoneableConfigMap(ConfigMap configMap, UnaryOperator<ConfigMap> action) {
        super(configMap);
        this.action = action;
    }

    public ConfigMap done() {
        ConfigMap built = new ConfigMap(getApiVersion(), getBinaryData(), getData(), getKind(), buildMetadata());
        return action.apply(built);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return hashCode.hashCode();
    }
}
