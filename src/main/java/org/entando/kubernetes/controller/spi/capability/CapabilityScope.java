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

package org.entando.kubernetes.controller.spi.capability;

public enum CapabilityScope implements NamedEnum{
    /*resolved using customResource.metadata.name + "-" + standardCapbility.getSuffix()*/
    DEDICATED,
    /*resolved using requiredCapability.specifiedCapability*/
    SPECIFIED,
    /*resolved using the first capability found in the same namespace with a matching implementation*/
    NAMESPACE,
    /*resolved using the first capability found with the matching labels and a matching implementation*/
    LABELED,
    /*resolved using the first capability found in the cluster with a matching implementation and operatorId*/
    CLUSTER
}
