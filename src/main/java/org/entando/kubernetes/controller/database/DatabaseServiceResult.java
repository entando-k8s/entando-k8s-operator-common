package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Optional;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.model.DbmsImageVendor;

public class DatabaseServiceResult extends AbstractServiceResult {

    private final DbmsImageVendor vendor;
    private final String databaseName;
    private final String databaseSecretName;
    private final Optional<Pod> pod;

    public DatabaseServiceResult(Service service, DbmsImageVendor vendor, String databaseName,
            String databaseSecretName, Optional<Pod> pod) {
        super(service);
        this.vendor = vendor;
        this.databaseName = databaseName;
        this.databaseSecretName = databaseSecretName;
        this.pod = pod;
    }

    public String getDatabaseSecretName() {
        return databaseSecretName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public DbmsImageVendor getVendor() {
        return vendor;
    }

    public boolean hasFailed() {
        return pod.map(existingPod -> PodResult.of(existingPod).hasFailed()).orElse(false);
    }

}
