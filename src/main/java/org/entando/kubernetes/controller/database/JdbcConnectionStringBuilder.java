package org.entando.kubernetes.controller.database;

import java.util.Map;
import java.util.stream.Collectors;

public abstract class JdbcConnectionStringBuilder {

    private String host;
    private String port;
    private String database;
    private String schema;
    private Map<String, String> parameters;

    public JdbcConnectionStringBuilder() {
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
