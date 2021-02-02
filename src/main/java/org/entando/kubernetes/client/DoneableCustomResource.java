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

import io.fabric8.kubernetes.api.model.Doneable;
import java.util.function.Function;

//UnaryOperator would make more sense, but Fabric8 does introspection on these constructors and provides a Function
//Frankly this code is not used
@SuppressWarnings("java:S4276")
public class DoneableCustomResource implements Doneable<SerializedEntandoResource> {

    SerializedEntandoResource resource;
    Function<SerializedEntandoResource, SerializedEntandoResource> function;

    public DoneableCustomResource(SerializedEntandoResource resource,
            Function<SerializedEntandoResource, SerializedEntandoResource> function) {
        this.resource = resource;
        this.function = function;
    }

    public DoneableCustomResource(
            Function<SerializedEntandoResource, SerializedEntandoResource> function) {
        this.function = function;
    }

    @Override
    public SerializedEntandoResource done() {
        return function.apply(resource);
    }
}
