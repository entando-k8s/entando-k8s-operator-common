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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fabric8.zjsonpatch.internal.guava.Strings;
import java.util.Locale;
import org.entando.kubernetes.model.DbmsVendor;

public enum KubernetesProvider {
    KUBERNETES(false), OPENSHIFT(false), GKE(true);

    /**
     * True if the persistentVolumes for the provider in quest are created with root ownership and
     */
    private boolean requiresFsGroup;

    KubernetesProvider(boolean requiresFsGroup) {
        this.requiresFsGroup = requiresFsGroup;
    }

    public static KubernetesProvider fromValue(String value) {
        return Strings.isNullOrEmpty(value) ? null : valueOf(value.toUpperCase(Locale.getDefault()));
    }

    public boolean requiresFsGroup() {
        return requiresFsGroup;
    }
}
