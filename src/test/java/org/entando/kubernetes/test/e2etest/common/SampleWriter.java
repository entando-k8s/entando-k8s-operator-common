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

package org.entando.kubernetes.test.e2etest.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SampleWriter {

    public static final String EXAMPLE_FOLDER_PATH = "src/test/resources/crd/examples/";

    private SampleWriter() {
    }

    public static void writeSample(HasMetadata r, String name) {
        try {
            ensureFolderExists(EXAMPLE_FOLDER_PATH);
            YAMLFactory yf = new YAMLFactory();
            ObjectMapper mapper = new ObjectMapper(yf);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try (OutputStream fos = Files.newOutputStream(Paths.get(EXAMPLE_FOLDER_PATH, name + ".yml"));
                    SequenceWriter sw = mapper.writerWithDefaultPrettyPrinter().writeValues(fos)) {
                sw.write(r);
                sw.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }

    private static void ensureFolderExists(String folderPath) throws IOException {
        Path path = Paths.get(folderPath);
        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }
}
