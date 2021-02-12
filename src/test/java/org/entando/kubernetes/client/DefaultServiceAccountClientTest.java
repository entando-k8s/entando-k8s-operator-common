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

package org.entando.kubernetes.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import org.entando.kubernetes.model.app.EntandoApp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultServiceAccountClientTest extends AbstractK8SIntegrationTest {

    private final EntandoApp entandoApp = newTestEntandoApp();

    @Test
    void shouldFindPreviouslyCreatedServiceAccount() {
        //Given I have an existing serviceAccount with the annotation "test: 123"
        getSimpleK8SClient().serviceAccounts().findOrCreateServiceAccount(entandoApp, "my-serviceaccount")
                .editMetadata()
                .addToAnnotations("test", "123")
                .endMetadata()
                .done();
        //When I attempt to findOrCreate a service account with the same name
        final ServiceAccount done = getSimpleK8SClient().serviceAccounts().findOrCreateServiceAccount(entandoApp, "my-serviceaccount")
                .done();
        //Then it has the previously created annotation
        assertThat(done.getMetadata().getAnnotations().get("test"), is("123"));
    }

    @Test
    void shouldNotReplacePreviouslyCreatedRole() {
        //Given I have an existing Role with the annotation "created: first"
        getSimpleK8SClient().serviceAccounts().createRoleIfAbsent(entandoApp, new RoleBuilder()
                .editOrNewMetadata()
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .withName("my-role")
                .addToAnnotations("created", "first")
                .endMetadata().build());
        //When I attempt to create a new role with the same name, but with the annotation "created: second"
        getSimpleK8SClient().serviceAccounts().createRoleIfAbsent(entandoApp, new RoleBuilder()
                .editOrNewMetadata()
                .withName("my-role")
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .addToAnnotations("created", "second")
                .endMetadata().build());
        //Then it has left the previous role unchanged
        assertThat(getSimpleK8SClient().serviceAccounts().loadRole(entandoApp, "my-role").getMetadata().getAnnotations().get("created"),
                is("first"));
    }

    @Test
    void shouldNotReplacePreviouslyCreatedRoleBinding() {
        //Given I have a service account and a role
        getSimpleK8SClient().serviceAccounts().findOrCreateServiceAccount(entandoApp, "my-serviceaccount")
                .done();
        getSimpleK8SClient().serviceAccounts().createRoleIfAbsent(entandoApp, new RoleBuilder()
                .editOrNewMetadata()
                .withName("my-role")
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .endMetadata().build());

        //And I have an existing Rolebinding with the annotation "created: first"
        getSimpleK8SClient().serviceAccounts().createRoleBindingIfAbsent(entandoApp, new RoleBindingBuilder()
                .editOrNewMetadata()
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .withName("my-role-binding")
                .addToAnnotations("created", "first")
                .endMetadata()
                .withNewRoleRef()
                .withKind("Role")
                .withName("my-role")
                .endRoleRef()
                .addNewSubject()
                .withName("my-serviceaccount")
                .withKind("ServiceAccount")
                .withNamespace(entandoApp.getMetadata().getNamespace())
                .endSubject()
                .build());
        //When I attempt to create a new rolebinding with the same name, but with the annotation "created: second"
        getSimpleK8SClient().serviceAccounts().createRoleBindingIfAbsent(entandoApp, new RoleBindingBuilder()
                .editOrNewMetadata()
                .withName("my-role-binding")
                .addToAnnotations("created", "second")
                .endMetadata().build());
        //Then it has left the previous role unchanged
        assertThat(getSimpleK8SClient().serviceAccounts().loadRoleBinding(entandoApp, "my-role-binding").getMetadata().getAnnotations()
                        .get("created"),
                is("first"));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{entandoApp.getMetadata().getNamespace()};
    }
}
