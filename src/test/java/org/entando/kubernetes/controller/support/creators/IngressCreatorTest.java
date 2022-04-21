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

package org.entando.kubernetes.controller.support.creators;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValueBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.spi.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class IngressCreatorTest implements InProcessTestData {

    SimpleK8SClient<?> client = new SimpleK8SClientDouble();
    EntandoApp entandoApp = new EntandoAppBuilder(newTestEntandoApp())
            .editSpec()
            .withTlsSecretName("original-tls-secret")
            .withIngressHostName("originalhost.com")
            .endSpec()
            .build();

    @AfterEach
    @BeforeEach
    void cleanUp() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty());
    }

    @Test
    void testEdit() {
        SpringBootDeployable<EntandoAppSpec> deployable = new SpringBootDeployable<>(entandoApp, null,
                (DatabaseConnectionInfo) null);
        ServiceCreator serviceCreator = new ServiceCreator(entandoApp);
        serviceCreator.createService(client.services(), deployable);
        IngressCreator creator = new IngressCreator(entandoApp);
        creator.createIngress(client.ingresses(), deployable, serviceCreator.getService(),
                new ServerStatus(NameUtils.MAIN_QUALIFIER));
        assertThat(creator.getIngress().getSpec().getRules().get(0).getHost(), is("originalhost.com"));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getHosts().get(0), is("originalhost.com"));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getSecretName(), is("original-tls-secret"));
        EntandoApp newEntandoApp = new EntandoAppBuilder(newTestEntandoApp())
                .editSpec()
                .withTlsSecretName("new-tls-secret")
                .withIngressHostName("newhost.com")
                .endSpec()
                .build();
        IngressCreator editingCreator = new IngressCreator(newEntandoApp);
        editingCreator
                .createIngress(client.ingresses(),
                        new SpringBootDeployable<>(newEntandoApp, null, (DatabaseConnectionInfo) null),
                        serviceCreator.getService(),
                        new ServerStatus(NameUtils.MAIN_QUALIFIER));
        assertThat(editingCreator.getIngress().getSpec().getRules().get(0).getHost(), is("newhost.com"));
        assertThat(editingCreator.getIngress().getSpec().getTls().get(0).getHosts().get(0), is("newhost.com"));
        assertThat(editingCreator.getIngress().getSpec().getTls().get(0).getSecretName(), is("new-tls-secret"));

    }

    @Test
    void shouldTruncateTheIngressPathAnnotationNameIfContainerNameQualifierTooLong() {
        SpringBootDeployable<EntandoAppSpec> deployable = new TestSpringBootDeployable(entandoApp, null, null);
        ServiceCreator serviceCreator = new ServiceCreator(entandoApp);
        serviceCreator.createService(client.services(), deployable);
        IngressCreator creator = new IngressCreator(entandoApp);
        creator.createIngress(client.ingresses(), deployable, serviceCreator.getService(),
                new ServerStatus(NameUtils.MAIN_QUALIFIER));
        assertThat(creator.getIngress().getSpec().getRules().get(0).getHost(), is("originalhost.com"));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getHosts().get(0), is("originalhost.com"));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getSecretName(), is("original-tls-secret"));
        assertThat(creator.getIngress().getMetadata().getAnnotations().get("entando.org/my-app-very-very-very-path"),
                is("/k8s"));
    }

    @Test
    void shouldTruncateTheHostNameIfRequired() {
        var x50 = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        EntandoApp longNameEntandoApp = new EntandoAppBuilder(newTestEntandoApp())
                .editMetadata()
                .withName("my-app-" + x50 + x50 + x50)
                .endMetadata()
                .editSpec()
                .withIngressHostName(null)
                .withTlsSecretName("original-tls-secret")
                .endSpec()
                .build();

        SpringBootDeployable<EntandoAppSpec> deployable = new TestSpringBootDeployable(longNameEntandoApp, null, null);

        ServiceCreator serviceCreator = new ServiceCreator(longNameEntandoApp);
        serviceCreator.createService(client.services(), deployable);
        IngressCreator creator = new IngressCreator(longNameEntandoApp);
        creator.createIngress(client.ingresses(), deployable, serviceCreator.getService(),
                new ServerStatus(NameUtils.MAIN_QUALIFIER));

        String expectedHost = "my-app-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx-my-app-namespace.apps.autotest.eng-entando.com";
        assertThat(creator.getIngress().getSpec().getRules().get(0).getHost(), is(expectedHost));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getHosts().get(0), is(expectedHost));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getSecretName(), is("original-tls-secret"));
    }

    @Test
    void shouldManageAnEventualException() {
        SpringBootDeployable<EntandoAppSpec> deployable = new TestSpringBootDeployable(entandoApp, null, null);
        ServiceCreator serviceCreator = new ServiceCreator(entandoApp);
        serviceCreator.createService(client.services(), deployable);
        entandoApp.getMetadata().setName(null);
        IngressCreator creator = new IngressCreator(entandoApp);
        Ingress ingress = new IngressBuilder().withNewMetadata().withName("test-ing").endMetadata()
                .withNewSpec().addNewRule().withHttp(new HTTPIngressRuleValueBuilder()
                        .withPaths(new HTTPIngressPath(null, "testpath", "testpath2")).build())
                .endRule().endSpec().build();
        when(client.ingresses().loadIngress(any(), any())).thenReturn(ingress);
        final Service service = serviceCreator.getService();
        final IngressClient ingresses = client.ingresses();
        final ServerStatus serverStatus = new ServerStatus(NameUtils.MAIN_QUALIFIER);
        assertThrows(EntandoControllerException.class,
                () -> creator.createIngress(ingresses, deployable, service, serverStatus));
    }


    private class TestSpringBootDeployable extends SpringBootDeployable<EntandoAppSpec> {

        public TestSpringBootDeployable(EntandoApp entandoApp,
                SsoConnectionInfo ssoConnectionInfo,
                DatabaseConnectionInfo databaseConnectionInfo) {
            super(entandoApp, ssoConnectionInfo,
                    new LongNameQualifierSpringBootDeployableContainer(entandoApp, databaseConnectionInfo,
                            ssoConnectionInfo,
                            new SsoClientConfig("entando", "asdf", "asdf")));
        }
    }

    private class LongNameQualifierSpringBootDeployableContainer extends
            SampleSpringBootDeployableContainer<EntandoApp> {

        public LongNameQualifierSpringBootDeployableContainer(EntandoApp entandoApp,
                DatabaseConnectionInfo databaseConnectionInfo,
                SsoConnectionInfo ssoConnectionInfo,
                SsoClientConfig ssoClientConfig) {
            super(entandoApp, databaseConnectionInfo, ssoConnectionInfo, ssoClientConfig);
        }

        @Override
        public String getNameQualifier() {
            return "very-very-very-very-long-qualifier";
        }
    }
}
