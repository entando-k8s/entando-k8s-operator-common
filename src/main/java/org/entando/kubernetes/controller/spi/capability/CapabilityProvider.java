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

package org.entando.kubernetes.controller.spi.capability;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Watch;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.model.ResourceReference;

public class CapabilityProvider {

    private final SimpleCapabilityClient client;

    public CapabilityProvider(SimpleCapabilityClient client) {
        this.client = client;
    }

    public ProvidedCapability provideCapability(HasMetadata forResource, RequiredCapability requiredCapability) {
        final CapabilityScope requirementScope = requiredCapability.getCapabilityRequirementScope().orElse(CapabilityScope.NAMESPACE);
        Optional<ConfigMap> match = findCapability(forResource, requiredCapability, requirementScope);
        match.ifPresent(c -> validateCapabilityCriteria(requiredCapability, requirementScope, c));
        return new ProvidedCapability(match.orElse(makeNewCapabilityAvailable(forResource, requiredCapability, requirementScope)));
    }

    private void validateCapabilityCriteria(RequiredCapability requiredCapability, CapabilityScope requirementScope, ConfigMap c) {
        if (!requirementScope.getCamelCaseName()
                .equals(c.getMetadata().getLabels().get(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME))) {
            throw new IllegalArgumentException(
                    format("The capability %s was found, but its provision scope is %s instead of the requested %s scope",
                            requiredCapability.getCapability().getCamelCaseName(),
                            c.getMetadata().getLabels().get(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME),
                            requirementScope.getCamelCaseName()
                    ));
        }
        if (!requiredCapability.getImplementation().map(i -> i.getCamelCaseName()
                .equals(c.getMetadata().getLabels().get(ProvidedCapability.IMPLEMENTATION_LABEL_NAME))).orElse(true)) {
            throw new IllegalArgumentException(
                    format("The capability %s was found, but its implementation is %s instead of the requested %s scope",
                            requiredCapability.getCapability().getCamelCaseName(),
                            c.getMetadata().getLabels().get(ProvidedCapability.IMPLEMENTATION_LABEL_NAME),
                            requirementScope.getCamelCaseName()
                    ));
        }
    }

    private Optional<ConfigMap> findCapability(HasMetadata forResource, RequiredCapability requiredCapability,
            CapabilityScope requirementScope) {
        switch (requirementScope) {
            case DEDICATED:
                return client.configMapByName(forResource.getMetadata().getNamespace(),
                        forResource.getMetadata().getName() + "-" + requiredCapability.getCapability().getSuffix());
            case SPECIFIED:
                final ResourceReference resourceReference = requiredCapability.getSpecifiedCapability()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "A requirement for a specified capability needs a valid name and optional namespace to resolve "
                                        + "the required capability."));
                return client.configMapByName(resourceReference.getNamespace().orElse(forResource.getMetadata().getNamespace()),
                        resourceReference.getName());
            case LABELED:
                if (requiredCapability.getAdditionalLabelsToMatch() == null || requiredCapability.getAdditionalLabelsToMatch().isEmpty()) {
                    throw new IllegalArgumentException("A requirement for a labeled capability needs at least one label to resolve "
                            + "the required capability.");
                }

                return client.configMapByLabels(requiredCapability.getAdditionalLabelsToMatch());
            case NAMESPACE:
                return client.configMapByLabels(forResource.getMetadata().getNamespace(), requiredCapability.getCapabilityLabels());
            case CLUSTER:
            default:
                return client.configMapByLabels(requiredCapability.getCapabilityLabels());
        }
    }

    private ConfigMap makeNewCapabilityAvailable(HasMetadata forResource, RequiredCapability requiredCapability,
            CapabilityScope requirementScope) {
        final CapabilityRequirement capabilityRequirement = buildCapabilityRequirementFor(forResource, requiredCapability);
        createAndWaitForCapability(capabilityRequirement);
        return findCapability(forResource, requiredCapability, requirementScope).orElseThrow(IllegalStateException::new);
    }

    private CapabilityRequirement buildCapabilityRequirementFor(HasMetadata forResource, RequiredCapability requiredCapability) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder();
        switch (requiredCapability.getCapabilityRequirementScope().orElse(CapabilityScope.NAMESPACE)) {
            case DEDICATED:
                metaBuilder = metaBuilder.withNamespace(forResource.getMetadata().getNamespace())
                        .withName(forResource.getMetadata().getName() + "-" + requiredCapability.getCapability().getSuffix());
                break;
            case SPECIFIED:
                final ResourceReference resourceReference = requiredCapability.getSpecifiedCapability()
                        .orElseThrow(IllegalStateException::new);
                metaBuilder = metaBuilder.withNamespace(resourceReference.getNamespace().orElse(forResource.getMetadata().getNamespace()))
                        .withName(resourceReference.getName());
                break;
            case LABELED:
                metaBuilder = metaBuilder.withNamespace(forResource.getMetadata().getNamespace())
                        .withName(
                                requiredCapability.getImplementation().map(i -> i.getHypenatedName() + "-").orElse("") + requiredCapability
                                        .getCapability().getHypenatedName() + "-" + NameUtils
                                        .randomNumeric(4));
                break;
            case NAMESPACE:
                metaBuilder = metaBuilder.withNamespace(forResource.getMetadata().getNamespace())
                        .withName(calculateDefaultName(requiredCapability));
                break;
            case CLUSTER:
            default:
                metaBuilder = metaBuilder.withNamespace(client.getNamespace())
                        .withName(calculateDefaultName(requiredCapability));
        }
        return new CapabilityRequirement(metaBuilder.build(), requiredCapability);
    }

    private String calculateDefaultName(RequiredCapability requiredCapability) {
        return "default" + requiredCapability.getImplementation()
                .map(i -> "-" + i.getHypenatedName()).orElse("") + "-" + requiredCapability.getCapability()
                .getHypenatedName();
    }

    void createAndWaitForCapability(CapabilityRequirement capabilityRequirement) {
        try {
            Object mutex = new Object();
            synchronized (mutex) {
                final CapabilityRequirementWatcher watcher = new CapabilityRequirementWatcher(mutex);
                try (Watch ignored = client.createAndWatchResource(capabilityRequirement, watcher)) {
                    //Sonar seems to believe the JVM may not respect wait() with timeout due to 'Spurious wakeups'
                    while (watcher.shouldStillWait()) {
                        mutex.wait(1000);
                    }
                    if (watcher.hasTimedOut()) {
                        throw new IllegalStateException("CapabilityRequirement deployment did not  within 5 minutes");
                    }
                    if (watcher.hasFailed()) {
                        throw new IllegalStateException("CapabilityRequirement deployment failed.");
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
