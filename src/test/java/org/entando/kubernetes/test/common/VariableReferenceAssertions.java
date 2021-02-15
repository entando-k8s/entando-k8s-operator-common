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

package org.entando.kubernetes.test.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Collection;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface VariableReferenceAssertions {

    default void verifyThatAllVariablesAreMapped(EntandoCustomResource resource, SimpleK8SClient client,
            Deployment deployment) {
        deployment.getSpec().getTemplate().getSpec().getContainers().stream().map(Container::getEnv)
                .flatMap(Collection::stream)
                .forEach(envVar -> {
                    if (envVar.getValueFrom() == null) {
                        assertThat(envVar.getName() + " has no value", envVar.getValue(), is(notNullValue()));
                    } else if (envVar.getValueFrom().getSecretKeyRef() != null) {
                        SecretKeySelector secretKeyRef = envVar.getValueFrom().getSecretKeyRef();
                        Secret secret = client.secrets().loadSecret(resource, secretKeyRef.getName());
                        assertThat("The secret " + secretKeyRef.getName() + " does not exist", secret, is(notNullValue()));
                        assertThat("The key " + secretKeyRef.getKey() + " does not exist on " + secretKeyRef.getName(),
                                secret.getStringData().get(secretKeyRef.getKey()), is(notNullValue()));

                    }

                });
    }
}
