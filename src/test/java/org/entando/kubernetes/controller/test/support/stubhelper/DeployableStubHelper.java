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

import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.database.DbmsDockerVendorStrategy;
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
    public static DatabaseDeployable<EntandoDatabaseService> stubDatabaseDeployable() {

        return new DatabaseDeployable(DbmsDockerVendorStrategy.MYSQL, new EntandoDatabaseService(), DEPLOYABLE_NAME_QUALIFIER);
    }

}
