package org.entando.kubernetes.controller;

import static java.lang.String.format;
import static java.util.Optional.empty;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.client.DefaultKeycloakClient;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.app.EntandoBaseCustomResource;
import org.entando.kubernetes.model.externaldatabase.ExternalDatabaseSpec;

public abstract class AbstractDbAwareController<T extends EntandoBaseCustomResource> {

    protected final SimpleK8SClient<?> k8sClient;
    protected final SimpleKeycloakClient keycloakClient;
    protected final AutoExit autoExit;
    protected Class<T> resourceType;
    protected Logger logger;

    /**
     * Constructor for runtime environments where the KubernetesClient is injected, and the controller is assumed to exit automatically to
     * emulate the behavior of a normal CLI.
     */

    protected AbstractDbAwareController(KubernetesClient kubernetesClient) {
        this(new DefaultSimpleK8SClient(kubernetesClient), new DefaultKeycloakClient(), new AutoExit(true));
    }

    /**
     * Constructor for in process tests where we may want to mock the clients out and would not want to exit.
     */

    protected AbstractDbAwareController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        this(k8sClient, keycloakClient, new AutoExit(false));
    }

    /**
     * Constructor for integration tests where we would need to override the auto exit behaviour.
     */
    public AbstractDbAwareController(KubernetesClient kubernetesClient, boolean exitAutomatically) {
        this(new DefaultSimpleK8SClient(kubernetesClient), new DefaultKeycloakClient(), new AutoExit(exitAutomatically));

    }

    @SuppressWarnings("unchecked")
    private AbstractDbAwareController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient, AutoExit autoExit) {
        this.k8sClient = k8sClient;
        this.keycloakClient = keycloakClient;
        this.autoExit = autoExit;
        Class<?> cls = getClass();
        while (cls.getSuperclass() != AbstractDbAwareController.class) {
            cls = cls.getSuperclass();
        }
        if (!isImplementedCorrectly(cls)) {
            throw new IllegalStateException(
                    "Please implement " + AbstractDbAwareController.class.getSimpleName() + " directly with the required type argument.");
        }
        ParameterizedType genericSuperclass = (ParameterizedType) cls.getGenericSuperclass();
        this.resourceType = (Class) genericSuperclass.getActualTypeArguments()[0];
        this.logger = Logger.getLogger(resourceType.getName() + "Controller");
    }

    private boolean isImplementedCorrectly(Class<?> cls) {
        if (cls.getGenericSuperclass() instanceof ParameterizedType) {
            ParameterizedType genericSuperclass = (ParameterizedType) cls.getGenericSuperclass();
            if (genericSuperclass.getActualTypeArguments().length == 1) {
                Class argument = (Class) genericSuperclass.getActualTypeArguments()[0];
                if (EntandoBaseCustomResource.class.isAssignableFrom(argument)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected abstract void processAddition(T newResource);

    protected void processCommand() {
        try {
            Action action = Action.valueOf(
                    EntandoOperatorConfig.lookupProperty(KubeUtils.ENTANDO_RESOURCE_ACTION).orElseThrow(IllegalArgumentException::new));
            switch (action) {
                case ADDED:
                    String resourceName = EntandoOperatorConfig.lookupProperty(KubeUtils.ENTANDO_RESOURCE_NAME)
                            .orElseThrow(IllegalArgumentException::new);
                    String resourceNamespace = EntandoOperatorConfig.lookupProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE)
                            .orElseThrow(IllegalArgumentException::new);
                    T newResource = k8sClient.entandoResources().load(resourceType, resourceNamespace, resourceName);
                    try {
                        k8sClient.entandoResources().updatePhase(newResource, EntandoDeploymentPhase.STARTED);
                        processAddition(newResource);
                        k8sClient.entandoResources().updatePhase(newResource, EntandoDeploymentPhase.SUCCESSFUL);
                    } catch (Exception e) {
                        autoExit.withCode(-1);
                        logger.log(Level.SEVERE, e, () -> format("Unexpected exception occurred while adding %s %s/%s",
                                newResource.getKind(),
                                newResource.getMetadata().getNamespace(),
                                newResource.getMetadata().getName()));
                        k8sClient.entandoResources().deploymentFailed(newResource, e);
                    }
                    break;
                default:
                    break;
            }
        } finally {
            new Thread(autoExit).start();
        }
    }

    protected DatabaseServiceResult prepareDatabaseService(EntandoCustomResource entandoCustomResource,
            Optional<DbmsImageVendor> dbmsImageVendor, String nameQualifier) {
        ExternalDatabaseDeployment externalDatabase = k8sClient.entandoResources().findExternalDatabase(entandoCustomResource);
        DatabaseServiceResult result;
        if (externalDatabase == null) {
            DbmsImageVendor dbmsVendor = dbmsImageVendor.orElse(DbmsImageVendor.POSTGRESQL);
            final DatabaseDeployable databaseDeployable = new DatabaseDeployable(dbmsVendor, entandoCustomResource, nameQualifier);
            final DeployCommand<DatabaseServiceResult> dbCommand = new DeployCommand<>(databaseDeployable);
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
