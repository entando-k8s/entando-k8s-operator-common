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

package org.entando.kubernetes.controller.unittest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Locale;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.controller.support.common.OperatorProcessingInstruction;
import org.entando.kubernetes.model.app.EntandoApp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class KubeUtilsTest {

    @Test
    void testResolveInstruction() {
        EntandoApp app = new EntandoApp();
        assertThat(KubeUtils.resolveProcessingInstruction(app), is(OperatorProcessingInstruction.NONE));
        app.getMetadata().setAnnotations(new HashMap<>());
        assertThat(KubeUtils.resolveProcessingInstruction(app), is(OperatorProcessingInstruction.NONE));
        app.getMetadata().getAnnotations().put(KubeUtils.PROCESSING_INSTRUCTION_ANNOTATION_NAME,
                OperatorProcessingInstruction.IGNORE.name().toLowerCase(Locale.ROOT));
        assertThat(KubeUtils.resolveProcessingInstruction(app), is(OperatorProcessingInstruction.IGNORE));
        app.getMetadata().getAnnotations().remove(KubeUtils.PROCESSING_INSTRUCTION_ANNOTATION_NAME);
        assertThat(KubeUtils.resolveProcessingInstruction(app), is(OperatorProcessingInstruction.NONE));
    }
}
