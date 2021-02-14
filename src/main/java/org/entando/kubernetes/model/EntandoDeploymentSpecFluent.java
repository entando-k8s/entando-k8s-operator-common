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

package org.entando.kubernetes.model;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;

public abstract class EntandoDeploymentSpecFluent<F extends EntandoDeploymentSpecFluent<F>> {

    protected Integer replicas = 1;
    protected String serviceAccountToUse;
    protected List<EnvVar> environmentVariables;
    protected EntandoResourceRequirements resourceRequirements;

    protected EntandoDeploymentSpecFluent() {

    }

    protected EntandoDeploymentSpecFluent(EntandoDeploymentSpec spec) {
        this.replicas = spec.getReplicas().orElse(null);
        this.serviceAccountToUse = spec.getServiceAccountToUse().orElse(null);
        this.environmentVariables = new ArrayList<>(spec.getEnvironmentVariables());
        this.resourceRequirements = spec.getResourceRequirements().orElse(null);

    }

    public final F withServiceAccountToUse(String serviceAccountToUse) {
        this.serviceAccountToUse = serviceAccountToUse;
        return thisAsF();
    }

    public final F withReplicas(Integer replicas) {
        this.replicas = replicas;
        return thisAsF();
    }

    public F withEnvironmentVariables(List<EnvVar> environmentVariables) {
        this.environmentVariables = new ArrayList<>(environmentVariables);
        return thisAsF();
    }

    public F addToEnvironmentVariables(String name, String value) {
        if (this.environmentVariables == null) {
            this.environmentVariables = new ArrayList<>();
        }
        this.environmentVariables.add(new EnvVar(name, value, null));
        return thisAsF();
    }

    public EntandoResourceRequirementsNested editResourceRequirements() {
        return new EntandoResourceRequirementsNested(thisAsF(), resourceRequirements);
    }

    public EntandoResourceRequirementsNested withNewResourceRequirements() {
        return new EntandoResourceRequirementsNested(thisAsF());
    }

    public F withResourceRequirements(EntandoResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
        return thisAsF();
    }

    @SuppressWarnings("unchecked")
    protected F thisAsF() {
        return (F) this;
    }

    public class EntandoResourceRequirementsNested extends
            EntandoResourceRequirementsFluent<EntandoResourceRequirementsNested> {

        private F parent;

        public EntandoResourceRequirementsNested(F parent, EntandoResourceRequirements resourceRequirements) {
            super(resourceRequirements);
            this.parent = parent;
        }

        public EntandoResourceRequirementsNested(F parent) {
            this.parent = parent;
        }

        public F done() {
            this.parent.withResourceRequirements(build());
            return this.parent;
        }

        public F endResourceRequirements() {
            return done();
        }
    }
}
