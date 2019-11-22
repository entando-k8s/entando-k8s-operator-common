package org.entando.kubernetes.controller.spi;

import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;

public interface DbAwareDeployable {

    String getNameQualifier();

    List<DeployableContainer> getContainers();

    default List<DbAware> getDbAwareContainers() {
        return getContainers().stream().filter(DbAware.class::isInstance)
                .map(DbAware.class::cast).collect(Collectors.toList());

    }

    DatabaseServiceResult getDatabaseServiceResult();
}
