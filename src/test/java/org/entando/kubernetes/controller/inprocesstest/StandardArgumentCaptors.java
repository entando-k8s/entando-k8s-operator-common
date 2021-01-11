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

package org.entando.kubernetes.controller.inprocesstest;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Map;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.KeycloakClientConfigArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.NamedArgumentCaptor;

public interface StandardArgumentCaptors {

    default KeycloakClientConfigArgumentCaptor forClientId(String clientid) {
        return KeycloakClientConfigArgumentCaptor.forClientId(clientid);
    }

    default <U extends HasMetadata, S extends U> LabeledArgumentCaptor<U> forResourceWithLabel(Class<S> clazz,
            String labelname, String labelValue) {
        return LabeledArgumentCaptor.forResourceWithLabel(clazz, labelname, labelValue);
    }

    default <U extends HasMetadata, S extends U> LabeledArgumentCaptor<U> forResourceWithLabels(Class<S> clazz,
            Map<String, String> labels) {
        return LabeledArgumentCaptor.forResourceWithLabels(clazz, labels);
    }

    default <U extends HasMetadata, S extends U> NamedArgumentCaptor<U> forResourceNamed(Class<S> clazz,
            String name) {
        return NamedArgumentCaptor.forResourceNamed(clazz, name);
    }

}
