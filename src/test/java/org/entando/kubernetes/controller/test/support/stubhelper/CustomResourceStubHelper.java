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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class CustomResourceStubHelper {

    public static final String CR_NAME = "cr_name";
    public static final String CR_NAMESPACE = "cr_namespace";

    /**
     * stubs a ObjectMeta filled with test data.
     * @return an ObjectMeta filled with test data
     */
    public static ObjectMeta stubObjectMeta() {

        ObjectMeta meta = new ObjectMeta();
        meta.setName(CR_NAME);
        meta.setNamespace(CR_NAMESPACE);
        return meta;
    }

    /**
     * stubs a EntandoDatabaseService filled with test data.
     * @return an EntandoDatabaseService filled with test data
     */
    public static EntandoDatabaseService stubEntandoDatabaseService() {

        return new EntandoDatabaseService(stubObjectMeta(), null);
    }
}
