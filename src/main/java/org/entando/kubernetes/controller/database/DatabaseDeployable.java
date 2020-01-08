package org.entando.kubernetes.controller.database;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.spi.Deployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.controller.spi.HasHealthCommand;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.Secretive;
import org.entando.kubernetes.controller.spi.ServiceBackingContainer;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DatabaseDeployable implements Deployable<DatabaseServiceResult>, Secretive {

    private final Map<DbmsImageVendor, VariableInitializer> variableInitializers = new ConcurrentHashMap<>();
    private final DbmsImageVendor dbmsVendor;
    private final EntandoCustomResource customResource;
    private final List<DeployableContainer> containers;
    private final String nameQualifier;

    public DatabaseDeployable(DbmsImageVendor dbmsVendor,
            EntandoCustomResource customResource, String nameQualifier) {
        variableInitializers.put(DbmsImageVendor.MYSQL, vars ->
                //No DB creation. Dbs are created during schema creation
                vars.add(new EnvVar("MYSQL_ROOT_PASSWORD", null,
                        KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)))
        );
        variableInitializers.put(DbmsImageVendor.POSTGRESQL, vars -> {
            vars.add(new EnvVar("POSTGRESQL_DATABASE", getDatabaseName(), null));
            // This username will not be used, as we will be creating schema/user pairs,
            // but without it the DB isn't created.
            vars.add(new EnvVar("POSTGRESQL_USER", getDatabaseName() + "_user", null));
            vars.add(new EnvVar("POSTGRESQL_PASSWORD", null,
                    KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));
            vars.add(new EnvVar("POSTGRESQL_ADMIN_PASSWORD", null,
                    KubeUtils.secretKeyRef(getDatabaseAdminSecretName(), KubeUtils.PASSSWORD_KEY)));

        });
        this.dbmsVendor = dbmsVendor;
        this.customResource = customResource;
        containers = Arrays.asList(new DatabaseContainer(this.variableInitializers, dbmsVendor, nameQualifier));
        this.nameQualifier = nameQualifier;
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return nameQualifier;
    }

    @Override
    public EntandoCustomResource getCustomResource() {
        return customResource;
    }

    @Override
    public DatabaseServiceResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new DatabaseServiceResult(service, dbmsVendor, getDatabaseName(), getDatabaseAdminSecretName(),
                ofNullable(pod));
    }

    @Override
    public List<Secret> buildSecrets() {
        Secret secret = KubeUtils.generateSecret(customResource, getDatabaseAdminSecretName(),
                dbmsVendor.getDefaultAdminUsername());
        return Arrays.asList(secret);
    }

    private String getDatabaseAdminSecretName() {
        return customResource.getMetadata().getName() + "-" + getNameQualifier()
                + "-admin-secret";
    }

    private String getDatabaseName() {
        return KubeUtils
                .snakeCaseOf(customResource.getMetadata().getName() + "_" + getNameQualifier());
    }

    interface VariableInitializer {

        void addEnvironmentVariables(List<EnvVar> vars);
    }

    public static class DatabaseContainer implements ServiceBackingContainer, PersistentVolumeAware, HasHealthCommand {

        private final Map<DbmsImageVendor, VariableInitializer> variableInitializers;
        private final DbmsImageVendor dbmsVendor;
        private final String nameQualifier;

        public DatabaseContainer(Map<DbmsImageVendor, VariableInitializer> variableInitializers,
                DbmsImageVendor dbmsVendor,
                String nameQualifier) {
            this.variableInitializers = variableInitializers;
            this.dbmsVendor = dbmsVendor;
            this.nameQualifier = nameQualifier;
        }

        @Override
        public String determineImageToUse() {
            return dbmsVendor.getImageName();
        }

        @Override
        public String getNameQualifier() {
            return nameQualifier;
        }

        @Override
        public int getPort() {
            return dbmsVendor.getPort();
        }

        @Override
        public String getVolumeMountPath() {
            return dbmsVendor.getVolumeMountPath();
        }

        @Override
        public String getHealthCheckCommand() {
            return dbmsVendor.getHealthCheck();
        }

        @Override
        public void addEnvironmentVariables(List<EnvVar> vars) {
            ofNullable(variableInitializers.get(dbmsVendor))
                    .orElseThrow(() -> new IllegalStateException(dbmsVendor + " not supported for container creation"))
                    .addEnvironmentVariables(vars);
        }

    }
}
