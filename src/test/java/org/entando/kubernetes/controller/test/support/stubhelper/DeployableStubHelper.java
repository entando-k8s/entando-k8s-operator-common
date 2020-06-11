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

package org.entando.kubernetes.controller.test.support.stubhelper;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.database.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class DeployableStubHelper {

    public static final String DEPLOYABLE_NAME_QUALIFIER = "nq-test";
    public static final int QTY_REQUESTS_CPU = 0;
    public static final int QTY_REQUESTS_MEM = 1;
    public static final int QTY_LIMITS_CPU = 2;
    public static final int QTY_LIMITS_MEM = 3;

    /**
     * creates and returns a stub DatabaseDeployable filled with test data.
     *
     * @return a DatabaseDeployable
     */
    public static DatabaseDeployable stubDatabaseDeployable() {

        return new DatabaseDeployable(DbmsDockerVendorStrategy.MYSQL, new EntandoDatabaseService(), DEPLOYABLE_NAME_QUALIFIER);
    }


    /**
     * creates and returns a list representing the limits and requests resources for the received Deployment.
     *
     * @return a list of Quantity. {@link QTY_LIMITS_CPU} {@link QTY_LIMITS_MEM} {@link QTY_REQUESTS_CPU} {@link QTY_REQUESTS_MEM}
     */
    public static List<Quantity> stubResourceQuantities() {

        return stubResourceQuantities(stubDatabaseDeployable());
    }


    /**
     * creates and returns a list representing the limits and requests resources for the received Deployment.
     *
     * @return a list of Quantity. {@link QTY_LIMITS_CPU} {@link QTY_LIMITS_MEM} {@link QTY_REQUESTS_CPU} {@link QTY_REQUESTS_MEM}
     */
    public static List<Quantity> stubResourceQuantities(Deployable deployable) {

        DeployableContainer deployableContainer = (DeployableContainer) deployable.getContainers().get(0);

        return Arrays.asList(
                new Quantity(deployableContainer.getCpuLimitMillicores() / 4 + "", "m"),        // request cpu
                new Quantity(deployableContainer.getMemoryLimitMebibytes() / 4 + "", "Mi"),     // request mem
                new Quantity(deployableContainer.getCpuLimitMillicores() + "", "m"),            // limit cpu
                new Quantity(deployableContainer.getMemoryLimitMebibytes() + "", "Mi")          // limit mem
        );
    }
}
