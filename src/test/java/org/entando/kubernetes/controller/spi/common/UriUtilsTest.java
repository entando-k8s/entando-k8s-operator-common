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

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("unit")})
class UriUtilsTest {


    @AfterEach
    @BeforeEach
    void cleanUp() {
    }

    @Test
    void testComposeRedirectUri() {
        final String expctedUrl1 = "http://server.com/mycontextpath/*";
        String calculatedUrl1 = UriUtils.composeUriOrThrow("http://server.com/", "/mycontextpath", "/*");
        assertThat(calculatedUrl1, Is.is(expctedUrl1));

        final String expctedUrl2 = "http://server.com/*";
        String calculatedUrl2 = UriUtils.composeUriOrThrow("http://server.com", "/", "/*");
        assertThat(calculatedUrl2, Is.is(expctedUrl2));

    }

}
