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

package org.entando.kubernetes.controller.unittest.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.entando.kubernetes.controller.database.DbmsVendorConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DbmsVendorConfigTest {

    @Test
    void testResolve() {
        assertThat(DbmsVendorConfig.MYSQL.getConnectionStringBuilder().onPort("123").toHost("localhost").usingDatabase("my_db")
                .usingSchema("my_schema")
                .buildConnectionString(), is("jdbc:mysql://localhost:123/my_schema"));
        assertThat(DbmsVendorConfig.POSTGRESQL.getConnectionStringBuilder().onPort("123").toHost("localhost").usingDatabase("my_db")
                .usingSchema("my_schema")
                .buildConnectionString(), is("jdbc:postgresql://localhost:123/my_db"));
        assertThat(DbmsVendorConfig.ORACLE.getConnectionStringBuilder().onPort("123").toHost("localhost").usingDatabase("my_db")
                .usingSchema("my_schema")
                .buildConnectionString(), is("jdbc:oracle:thin:@//localhost:123/my_db"));
        assertThat(DbmsVendorConfig.H2.getConnectionStringBuilder().inFolder("/entando-data/databases").usingDatabase("my_db")
                .buildConnectionString(), is("jdbc:h2:file:/entando-data/databases/my_db;DB_CLOSE_ON_EXIT=FALSE"));
        assertThat(DbmsVendorConfig.DERBY.getConnectionStringBuilder().inFolder("/entando-data/databases").usingDatabase("my_db")
                .buildConnectionString(), is("jdbc:derby:/entando-data/databases/my_db;create=true"));
    }
}
