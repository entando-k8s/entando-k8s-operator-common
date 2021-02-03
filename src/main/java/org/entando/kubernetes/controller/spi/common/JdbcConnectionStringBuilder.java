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

import java.util.Map;
import java.util.stream.Collectors;

public abstract class JdbcConnectionStringBuilder {

    private String host;
    private String port;
    private String database;
    private String schema;
    private String dataFolder;
    private Map<String, String> parameters;

    protected JdbcConnectionStringBuilder() {
    }

    public JdbcConnectionStringBuilder toHost(String host) {
        this.host = host;
        return this;
    }

    public JdbcConnectionStringBuilder onPort(String port) {
        this.port = port;
        return this;
    }

    public JdbcConnectionStringBuilder usingDatabase(String database) {
        this.database = database;
        return this;
    }

    public JdbcConnectionStringBuilder inFolder(String folder) {
        this.dataFolder = folder;
        return this;
    }

    public JdbcConnectionStringBuilder usingSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public JdbcConnectionStringBuilder usingParameters(Map<String, String> parameters) {
        this.parameters = parameters;
        return this;
    }

    public String getHost() {
        return this.host;
    }

    public String getPort() {
        return this.port;
    }

    public String getDatabase() {
        return this.database;
    }

    public String getDataFolder() {
        return dataFolder;
    }

    public String getSchema() {
        return this.schema;
    }

    public abstract String buildConnectionString();

    public String buildJdbcConnectionString() {
        return buildConnectionString() + parameterSuffix();
    }

    public String parameterSuffix() {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        } else {
            return "?" + parameters.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
        }
    }
}
