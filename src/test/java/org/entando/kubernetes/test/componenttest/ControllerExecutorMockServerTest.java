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

package org.entando.kubernetes.test.componenttest;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.junit.Rule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
@EnableRuleMigrationSupport
public class ControllerExecutorMockServerTest extends ControllerExecutorTestBase {

    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private DefaultSimpleK8SClient defaultSimpleK8SClient;

    @Override
    public SimpleK8SClient<?> getClient() {
        if (defaultSimpleK8SClient == null) {
            defaultSimpleK8SClient = new DefaultSimpleK8SClient(server.getClient());
        }
        return defaultSimpleK8SClient;
    }
}