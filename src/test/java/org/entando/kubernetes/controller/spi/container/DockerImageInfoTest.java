package org.entando.kubernetes.controller.spi.container;

import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DockerImageInfoTest {

    @Test
    void shouldSupportAnyNumberOfSegments() {

        Stream.of("entando/image:1.0.0",
                        "entando/segment/image:1.0.0",
                        "entando/segment/subsegment/image:1.0.0",
                        "cl/entando/segment/subsegment/image:1.0.0",
                        "registry.private.cloud/cl/entando/segment/subsegment/image:1.0.0")
                .forEach(s -> {
                    System.out.println("Testing: " + s);
                    final DockerImageInfo dockerImageInfo = new DockerImageInfo(s);
                });
    }
}
