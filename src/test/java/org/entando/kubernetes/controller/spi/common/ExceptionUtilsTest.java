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

package org.entando.kubernetes.controller.spi.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ExceptionUtilsTest {

    @Test
    void testIoSafe() {
        assertThatThrownBy(() -> ioSafe(() -> {
            throw new IOException();
        })).isInstanceOf(IllegalStateException.class);
    }
}
