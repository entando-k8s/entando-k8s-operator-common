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

package org.entando.kubernetes.controller.database;

import java.util.Locale;
import java.util.Optional;
import org.entando.kubernetes.model.DbmsVendor;

public enum DbmsDockerVendorStrategy {
    MYSQL(DbmsVendorConfig.MYSQL, "entando/mysql-57-centos7", "/var/lib/mysql/data", 27L) {
        @Override
        public boolean schemaIsDatabase() {
            return true;
        }
    },
    POSTGRESQL(DbmsVendorConfig.POSTGRESQL, "entando/postgresql-96-centos7", "/var/lib/pgsql/data", 26L),
    ORACLE(DbmsVendorConfig.ORACLE, "docker.io/store/oracle/database-enterprise:12.2.0.1", "/ORCL", null);

    public static final String DATABASE_IDENTIFIER_TYPE = "databaseIdentifierType";
    public static final String TABLESPACE_PARAMETER_NAME = "tablespace";
    private String imageName;
    private String volumeMountPath;
    private DbmsVendorConfig vendorConfig;
    private Long fsUserGroupId;

    DbmsDockerVendorStrategy(DbmsVendorConfig vendorConfig, String imageName, String volumeMountPath, Long fsUserGroupId) {
        this.imageName = imageName;
        this.volumeMountPath = volumeMountPath;
        this.vendorConfig = vendorConfig;
        this.fsUserGroupId = fsUserGroupId;
    }

    public Optional<Long> getFileSystemUserGroupid() {
        return Optional.ofNullable(fsUserGroupId);
    }

    public JdbcConnectionStringBuilder getConnectionStringBuilder() {
        return this.vendorConfig.getConnectionStringBuilder();
    }

    public DbmsVendorConfig getVendorConfig() {
        return vendorConfig;
    }

    public String getHealthCheck() {
        return this.vendorConfig.getHealthCheck();
    }

    public String getImageName() {
        return this.imageName;
    }

    public int getPort() {
        return this.vendorConfig.getDefaultPort();
    }

    public String getVolumeMountPath() {
        return this.volumeMountPath;
    }

    public String toValue() {
        return this.name().toLowerCase(Locale.getDefault());
    }

    public String getName() {
        return this.name().toLowerCase(Locale.getDefault());
    }

    public boolean schemaIsDatabase() {
        return false;
    }

    public String getDefaultAdminUsername() {
        return this.vendorConfig.getDefaultUser();
    }

    public String getHibernateDialect() {
        return this.vendorConfig.getHibernateDialect();
    }

    public static DbmsDockerVendorStrategy forVendor(DbmsVendor vendor) {
        return valueOf(vendor.name());
    }
}
