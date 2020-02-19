package org.entando.kubernetes.controller.database;

import java.util.Locale;
import org.entando.kubernetes.model.DbmsVendor;

public enum DbmsVendorStrategy {
    MYSQL("docker.io/centos/mysql-57-centos7:latest", 3306, "root", "/var/lib/mysql/data",
            "MYSQL_PWD=${MYSQL_ROOT_PASSWORD} mysql -h 127.0.0.1 -u root -e 'SELECT 1'", "org.hibernate.dialect.MySQL5InnoDBDialect") {
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:mysql://%s:%s/%s", this.getHost(), this.getPort(), this.getSchema());
                }
            };
        }

        public boolean schemaIsDatabase() {
            return true;
        }
    },
    POSTGRESQL("docker.io/centos/postgresql-96-centos7:latest", 5432, "postgres", "/var/lib/pgsql/data",
            "psql -h 127.0.0.1 -U ${POSTGRESQL_USER} -q -d postgres -c '\\l'|grep ${POSTGRESQL_DATABASE}",
            "org.hibernate.dialect.PostgreSQLDialect") {
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:postgresql://%s:%s/%s", this.getHost(), this.getPort(), this.getDatabase());
                }
            };
        }
    },
    ORACLE("docker.io/store/oracle/database-enterprise:12.2.0.1", 1521, "sys", "/ORCL", "sqlplus sys/Oradoc_db1:${DB_SID}",
            "org.hibernate.dialect.Oracle10gDialect") {
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:oracle:thin:@//%s:%s/%s", this.getHost(), this.getPort(), this.getDatabase());
                }
            };
        }
    };
    public static final String DATABASE_IDENTIFIER_TYPE = "databaseIdentifierType";
    public static final String TABLESPACE_PARAMETER_NAME = "tablespace";
    private String imageName;
    private int port;
    private String defaultAdminUsername;
    private String volumeMountPath;
    private String healthCheck;
    private String hibernateDialect;

    private DbmsVendorStrategy(String imageName, int port, String defaultAdminUsername, String volumeMountPath, String healthCheck,
            String hibernateDialect) {
        this.imageName = imageName;
        this.port = port;
        this.defaultAdminUsername = defaultAdminUsername;
        this.volumeMountPath = volumeMountPath;
        this.healthCheck = healthCheck;
        this.hibernateDialect = hibernateDialect;
    }

    public abstract JdbcConnectionStringBuilder getConnectionStringBuilder();

    public String getHealthCheck() {
        return this.healthCheck;
    }

    public String getImageName() {
        return this.imageName;
    }

    public int getPort() {
        return this.port;
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
        return this.defaultAdminUsername;
    }

    public String getHibernateDialect() {
        return this.hibernateDialect;
    }

    public static DbmsVendorStrategy forVendor(DbmsVendor vendor) {
        return valueOf(vendor.name());
    }
}