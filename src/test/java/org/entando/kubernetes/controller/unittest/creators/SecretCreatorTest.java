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

package org.entando.kubernetes.controller.unittest.creators;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.common.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.creators.SecretCreator;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class SecretCreatorTest implements InProcessTestUtil {

    private static final String MY_IMAGE_PULL_SECRET = "my-image-pull-secret";
    public static final String DOCKER_CONFIG_JSON = "{asdfasdfasd:asdfasdfasd}";
    SimpleK8SClient<?> client = new SimpleK8SClientDouble();
    EntandoApp entandoApp = new EntandoAppBuilder(newTestEntandoApp())
            .editSpec()
            .endSpec()
            .build();

    @AfterEach
    @BeforeEach
    void cleanUp() {
        System.getProperties()
                .remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS.getJvmSystemProperty());
    }

    @Test
    void testImagePullSecretPropagation() {
        //Given that the operator was configured with custom ImagePullSecrets
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS.getJvmSystemProperty(),
                MY_IMAGE_PULL_SECRET);
        //And that Secret is present in the Operator's namespace
        client.secrets().overwriteControllerSecret(new SecretBuilder()
                .withNewMetadata()
                .withName(MY_IMAGE_PULL_SECRET)
                .withNamespace(client.entandoResources().getNamespace())
                .endMetadata()
                .withType("kubernetes.io/dockercfg")
                .addToStringData(".dockerconfig", DOCKER_CONFIG_JSON)
                .build());
        SamplePublicIngressingDbAwareDeployable<EntandoAppSpec> deployable = new SamplePublicIngressingDbAwareDeployable<>(entandoApp, null,
                emulateKeycloakDeployment(client));
        //When the operator creates the secrets required for the deployment
        new SecretCreator<>(entandoApp).createSecrets(client.secrets(), deployable);
        //Then the image pull secret is present in the  deployment namespace
        Secret secret = client.secrets().loadSecret(entandoApp, MY_IMAGE_PULL_SECRET);
        assertThat(secret.getType(), is("kubernetes.io/dockercfg"));
        assertThat(secret.getStringData().get(".dockerconfig"), is(DOCKER_CONFIG_JSON));
    }
}
