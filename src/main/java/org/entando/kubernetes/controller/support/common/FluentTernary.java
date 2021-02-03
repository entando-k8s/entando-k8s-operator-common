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

package org.entando.kubernetes.controller.support.common;

public class FluentTernary<T> {

    private final T trueValue;
    private boolean condition;

    public FluentTernary(T trueValue) {
        this.trueValue = trueValue;
    }

    public static <T> FluentTernary<T> use(T trueValue) {
        return new FluentTernary<>(trueValue);
    }

    public static <T> FluentTernary<T> useNull(Class<T> clazz) {

        return new FluentTernary<>(clazz.cast(null));
    }

    public FluentTernary<T> when(boolean condition) {
        this.condition = condition;
        return this;
    }

    public T orElse(T falseValue) {
        return condition ? trueValue : falseValue;
    }
}
