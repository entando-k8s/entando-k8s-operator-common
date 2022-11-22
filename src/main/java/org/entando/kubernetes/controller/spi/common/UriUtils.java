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

import javax.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UriUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(UriUtils.class);

    private UriUtils() {}

    public static String composeRedirectUriOrThrow(String serverUrl, String path) {
        try {
            UriBuilder builder = UriBuilder.fromUri(serverUrl);
            builder.path(path).path("/*");
            return builder.build().toString();
            // FIXME replace Paths.get, it doesn't work with serverUrl (without +) or with wrong serverUrl
//            String endpath = Paths.get("/", path, "/*").toString();
//            return serverUrl + endpath;
        } catch (Exception ex) {
            LOGGER.error("Error composing url to use as RedirectUri inside IDP serverUrl:'{}', path'{}'", serverUrl,
                    path, ex);
            throw new RuntimeException(ex);
        }
    }


}
