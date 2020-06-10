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

package org.entando.kubernetes.controller.test.support.assertionhelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import java.util.List;
import org.entando.kubernetes.controller.test.support.stubhelper.DeployableStubHelper;

public class ResourceRequirementsAssertionHelper {


    /**
     * asserts on the received Quantity objects
     * @param expectedQuantities
     * @param actualResources
     */
    public static void assertQuantities(List<Quantity> expectedQuantities, ResourceRequirements actualResources) {

        assertQuantity(expectedQuantities.get(DeployableStubHelper.QTY_LIMITS_CPU), actualResources.getLimits().get("cpu"));
        assertQuantity(expectedQuantities.get(DeployableStubHelper.QTY_LIMITS_MEM), actualResources.getLimits().get("memory"));
        assertQuantity(expectedQuantities.get(DeployableStubHelper.QTY_REQUESTS_CPU), actualResources.getRequests().get("cpu"));
        assertQuantity(expectedQuantities.get(DeployableStubHelper.QTY_REQUESTS_MEM), actualResources.getRequests().get("memory"));
    }


    /**
     * asserts on the received Quantity objects
     * @param expected
     * @param actual
     */
    public static void assertQuantity(Quantity expected, Quantity actual) {
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(expected.getFormat(), actual.getFormat());
    }

}
