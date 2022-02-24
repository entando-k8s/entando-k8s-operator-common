package org.entando.kubernetes.controller.support.creators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.io.IOException;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class AbstractK8SResourceCreatorTest {

    private AbstractK8SResourceCreator resourceCreator;
    private YAMLMapper yamlMapper = new YAMLMapper();
    private EntandoPlugin plugin;

    @BeforeEach
    public void setup() throws IOException {
        plugin = yamlMapper.readValue(new File("src/test/resources/plugin-with-name.yml"),
                EntandoPlugin.class);
        resourceCreator = new AbstractK8SResourceCreator(plugin);
    }

    @Test
    void shouldTruncateKeepingQualifierAndSuffixWhileResolvingName() {
        plugin.getMetadata().setName("test-plugin-a-with.very.long.naaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaame");
        final String name = resourceCreator.generateName("myqualifier", "service");
        assertTrue(name.length() <= 63);
        assertEquals("test-plugin-a-with.very.long.naaaaaaaaaaaaa-myqualifier-service", name);
    }

    @Test
    void shouldProperlyGenerateLongNames() {
        String q = "myqualifier";
        String a50 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        plugin.getMetadata().setName("test-resource-with.very.long.na" + a50 + a50 + a50 + a50 + a50 + "me");
        //~
        String s;
        String name = resourceCreator.generateDeploymentName(q, s = "deployment");
        assertThat(name.length()).isLessThanOrEqualTo(253);
        assertEquals("test-resource-with.very.long.n" + a50 + a50 + a50 + a50 + "-" + q + "-" + s, name);
    }

    @Test
    void shouldReturnTheExpectedNameWhileResolvingIt() throws IOException {
        String name = resourceCreator.generateName("myqualifier", "service");
        assertTrue(name.length() <= 63);
        assertEquals("test-plugin-a-myqualifier-service", name);

        name = resourceCreator.generateName(null, "service");
        assertTrue(name.length() <= 63);
        assertEquals("test-plugin-a-service", name);

        name = resourceCreator.generateName("myqualifier", null);
        assertTrue(name.length() <= 63);
        assertEquals("test-plugin-a-myqualifier", name);
    }
}
