package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.DoneableEndpoints;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.HasMetadataOperation;
import java.util.Map;
import java.util.TreeMap;
import okhttp3.OkHttpClient;

/**
 * Fabric8's client classes are generated. Their generation algorithm is faulty when it comes to the Endpoints object which is already
 * plural. It incorrectly points to 'endpointss' instead of 'endpoints'. This class fixes this bug.
 */
public class FixedEndpointsOperation extends
        HasMetadataOperation<Endpoints, EndpointsList, DoneableEndpoints, Resource<Endpoints, DoneableEndpoints>> {

    public FixedEndpointsOperation(OkHttpClient client, Config config, String namespace) {
        this(client, config, "", "v1", namespace, (String) null, true, (Endpoints) null, (String) null, false, -1L, new TreeMap(),
                new TreeMap(), new TreeMap(), new TreeMap(), new TreeMap());
    }

    public FixedEndpointsOperation(OkHttpClient client, Config config, String apiGroupVersion, String namespace, String name,
            Boolean cascading, Endpoints item, String resourceVersion, Boolean reloadingFromServer, long gracePeriodSeconds,
            Map<String, String> labels, Map<String, String> labelsNot, Map<String, String[]> labelsIn, Map<String, String[]> labelsNotIn,
            Map<String, String> fields) {
        super(client, config, "", apiGroupVersion, "endpoints", namespace, name, cascading, item, resourceVersion, reloadingFromServer,
                gracePeriodSeconds, labels, labelsNot, labelsIn, labelsNotIn, fields);
    }

    public FixedEndpointsOperation(OkHttpClient client, Config config, String apiGroupName, String apiGroupVersion, String namespace,
            String name, Boolean cascading, Endpoints item, String resourceVersion, Boolean reloadingFromServer, long gracePeriodSeconds,
            Map<String, String> labels, Map<String, String> labelsNot, Map<String, String[]> labelsIn, Map<String, String[]> labelsNotIn,
            Map<String, String> fields) {
        super(client, config, apiGroupName, apiGroupVersion, "endpoints", namespace, name, cascading, item, resourceVersion,
                reloadingFromServer, gracePeriodSeconds, labels, labelsNot, labelsIn, labelsNotIn, fields);
    }

    @Override
    public FixedEndpointsOperation inNamespace(
            String namespace) {
        return new FixedEndpointsOperation(this.client, this.config, this.apiGroupName,
                this.apiGroupVersion, namespace, this.name, this.isCascading(), (Endpoints) this.getItem(), this.getResourceVersion(),
                this.isReloadingFromServer(), this.getGracePeriodSeconds(), this.getLabels(), this.getLabelsNot(), this.getLabelsIn(),
                this.getLabelsNotIn(), this.getFields());
    }

    @Override
    public FixedEndpointsOperation withName(String name) {
        return new FixedEndpointsOperation(this.client, this.config, this.apiGroupName,
                this.apiGroupVersion, this.namespace, name, this.isCascading(), (Endpoints) this.getItem(), this.getResourceVersion(),
                this.isReloadingFromServer(), this.getGracePeriodSeconds(), this.getLabels(), this.getLabelsNot(), this.getLabelsIn(),
                this.getLabelsNotIn(), this.getFields());
    }

    @Override
    public FixedEndpointsOperation fromServer() {
        return (FixedEndpointsOperation) super.fromServer();
    }
}
