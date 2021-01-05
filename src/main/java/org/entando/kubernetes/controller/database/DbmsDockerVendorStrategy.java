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
import org.entando.kubernetes.controller.EntandoOperatorComplianceMode;
import org.entando.kubernetes.model.DbmsVendor;

public enum DbmsDockerVendorStrategy {
    CENTOS_MYSQL(DbmsVendorConfig.MYSQL, "docker.io", "centos", "mysql-80-centos7", "/var/lib/mysql/data", 27L),
    CENTOS_POSTGRESQL(DbmsVendorConfig.POSTGRESQL, "docker.io", "centos", "postgresql-12-centos7", "/var/lib/pgsql/data", 26L),
    RHEL_MYSQL(DbmsVendorConfig.MYSQL, "registry.redhat.io", "rhel8", "mysql-80", "/var/lib/mysql/data", 27L),
    RHEL_POSTGRESQL(DbmsVendorConfig.POSTGRESQL, "registry.redhat.io", "rhel8", "postgresql-12", "/var/lib/pgsql/data", 26L),
    ORACLE(DbmsVendorConfig.ORACLE, "docker.io", "store/oracle", "database-enterprise:12.2.0.1", "/ORCL", null);

    public static final String DATABASE_IDENTIFIER_TYPE = "databaseIdentifierType";
    public static final String TABLESPACE_PARAMETER_NAME = "tablespace";
    private String imageRepository;
    private String organization;
    private String registry;
    private String volumeMountPath;
    private DbmsVendorConfig vendorConfig;
    private Long fsUserGroupId;

    DbmsDockerVendorStrategy(DbmsVendorConfig vendorConfig, String registry, String organization, String imageRepository,
            String volumeMountPath,
            Long fsUserGroupId) {
        this.registry = registry;
        this.organization = organization;
        this.imageRepository = imageRepository;
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

    public String getImageRepository() {
        return this.imageRepository;
    }

    public String getOrganization() {
        return organization;
    }

    public String getRegistry() {
        return registry;
    }

    public int getPort() {
        return this.vendorConfig.getDefaultPort();
    }

    public String getVolumeMountPath() {
        return this.volumeMountPath;
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

    public static DbmsDockerVendorStrategy forVendor(DbmsVendor vendor, EntandoOperatorComplianceMode complianceMode) {
        if (complianceMode == EntandoOperatorComplianceMode.COMMUNITY) {
            if (vendor == DbmsVendor.POSTGRESQL) {
                return CENTOS_POSTGRESQL;
            } else if (vendor == DbmsVendor.MYSQL) {
                return CENTOS_MYSQL;
            }
        } else if (complianceMode == EntandoOperatorComplianceMode.REDHAT) {
            if (vendor == DbmsVendor.POSTGRESQL) {
                return RHEL_POSTGRESQL;
            } else if (vendor == DbmsVendor.MYSQL) {
                return RHEL_MYSQL;
            }
        }
        return valueOf(vendor.name());
    }
}
