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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SampleWriter {

    private SampleWriter() {

    }

    public static Path writeSample(Path targetDir, EntandoCustomResource customResource) {
        try {
            YAMLFactory yf = new YAMLFactory();
            ObjectMapper mapper = new ObjectMapper(yf);
            Path path = Paths.get(targetDir.toString(),
                    customResource.getMetadata().getName() + customResource.getMetadata().getGeneration() + ".yml");
            try (OutputStream fos = Files.newOutputStream(
                    path);
                    SequenceWriter sw = mapper.writerWithDefaultPrettyPrinter().writeValues(fos)) {
                sw.write(customResource);
                sw.flush();
            }
            return path;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }

    public static <T extends EntandoCustomResource> T readSample(Path sourceFile, Class<T> type) {
        try {
            YAMLFactory yf = new YAMLFactory();
            ObjectMapper mapper = new ObjectMapper(yf);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try (InputStream fis = Files.newInputStream(sourceFile)) {
                ObjectReader sr = mapper.readerFor(type);
                return sr.readValue(fis);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }
}
