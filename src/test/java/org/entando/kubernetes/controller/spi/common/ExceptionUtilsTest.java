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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.retry;

import java.io.IOException;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ExceptionUtilsTest {

    private long start;

    @Test
    void testIoSafe() {
        assertThatThrownBy(() -> ioSafe(() -> {
            throw new IOException();
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRetry() {
        final ValueHolder<Integer> count = new ValueHolder<>();
        //Success
        this.start = System.currentTimeMillis();
        assertThat(retry(() -> "123", e -> e.getMessage().equals("Now"), 3)).isEqualTo("123");
        assertThat(System.currentTimeMillis() - start).isLessThan(100L);
        //Failed twice
        count.set(0);
        this.start = System.currentTimeMillis();
        assertThat(retry(() -> {
            count.set(count.get() + 1);
            if (count.get() < 2) {
                throw new RuntimeException("Now");
            }
            return count.get().toString();
        }, e -> e.getMessage().equals("Now"), 3)).isEqualTo("2");
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(1000L);
        //Failed more than allowed
        count.set(0);
        this.start = System.currentTimeMillis();
        assertThatThrownBy(() -> retry(() -> {
            count.set(count.get() + 1);
            if (count.get() < 4) {
                throw new RuntimeException("Now");
            }
            return count.get().toString();
        }, e -> e.getMessage().equals("Now"), 3)).matches(throwable -> throwable.getMessage().equals("Now"));
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(5000L);
        //Failed with unanticipated exception
        count.set(0);
        this.start = System.currentTimeMillis();
        assertThatThrownBy(() -> retry(() -> {
            count.set(count.get() + 1);
            if (count.get() < 4) {
                throw new RuntimeException("Then");
            }
            return count.get().toString();
        }, e -> e.getMessage().equals("Now"), 3)).matches(throwable -> throwable.getMessage().equals("Then"));
        assertThat(System.currentTimeMillis() - start).isLessThan(1000L);
    }
}
