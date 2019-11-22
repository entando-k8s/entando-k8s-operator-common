package org.entando.kubernetes.controller;

import static java.util.Optional.empty;

import java.util.Optional;
import javax.inject.Inject;
import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.EntandoResourceClient;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabaseSpec;

public class AbstractDbAwareController {

    @Inject
    protected SimpleK8SClient<EntandoResourceClient> k8sClient;

    protected DatabaseServiceResult prepareDatabaseService(EntandoCustomResource entandoCustomResource,
            Optional<DbmsImageVendor> dbmsImageVendor, String nameQualifier) {
        ExternalDatabaseDeployment externalDatabase = k8sClient.entandoResources().findExternalDatabase(entandoCustomResource);
        DatabaseServiceResult result;
        if (externalDatabase == null) {
            DbmsImageVendor dbmsVendor = dbmsImageVendor.orElse(DbmsImageVendor.POSTGRESQL);
            final DatabaseDeployable databaseDeployable = new DatabaseDeployable(dbmsVendor, entandoCustomResource, nameQualifier);
            final DeployCommand<DatabaseServiceResult> dbCommand = new DeployCommand(databaseDeployable);
            result = dbCommand.execute(k8sClient, empty());
            if (result.hasFailed()) {
                throw new EntandoControllerException("Database deployment failed");
            }
        } else {
            ExternalDatabaseSpec spec = externalDatabase.getExternalDatabase().getSpec();
            result = new DatabaseServiceResult(externalDatabase.getService(), spec.getDbms(), spec.getDatabaseName(),
                    spec.getSecretName(), empty());
        }
        return result;
    }
}
