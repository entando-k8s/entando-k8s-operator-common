package org.entando.kubernetes.controller.spi.container;

import static org.assertj.core.api.Java6Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DockerImageInfoTest {

    @Test
    void shouldSupportAnyNumberOfSegments() {
        // full / registry host+port / org / repo / tag
        Stream.of(new String[]{"entando/image:1.0.0", null, "entando", "image", "1.0.0"},
                        new String[]{"registry.private.cloud/entando/image:1.0.0", "registry.private.cloud", "entando",
                                "image", "1.0.0"},
                        new String[]{"registry.private.cloud/entando/segment/image:1.0.0", "registry.private.cloud",
                                "entando/segment",
                                "image", "1.0.0"},
                        new String[]{"registry.private.cloud:5000/entando/segment/subsegment/image:1.0.0",
                                "registry.private.cloud:5000",
                                "entando/segment/subsegment",
                                "image", "1.0.0"},
                        new String[]{"registry.private.cloud/cl/entando/segment/subsegment/image:1.0.0",
                                "registry.private.cloud", "cl/entando/segment/subsegment", "image", "1.0.0"})
                .forEach(s -> {
                    System.out.println("Testing: " + s[0]);
                    final DockerImageInfo dockerImageInfo = new DockerImageInfo(s[0]);
                    assertThat(new String[]{s[0], dockerImageInfo.getRegistry().orElse(null),
                            dockerImageInfo.getOrganization().orElse(null),
                            dockerImageInfo.getRepository(),
                            dockerImageInfo.getVersion().orElse(null)}).isEqualTo(s);
                });
    }

    @Test
    void shouldRaiseException() {
        Stream.of("entando/image::1.0.0", "registry.private.cloud::5000/entando/image:1.0.0")
                .forEach(s -> {
                    Assertions.assertThrows(IllegalArgumentException.class, () -> new DockerImageInfo(s));
                });
    }


}
