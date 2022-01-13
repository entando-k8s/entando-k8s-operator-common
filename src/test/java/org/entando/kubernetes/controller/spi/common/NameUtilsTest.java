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

package org.entando.kubernetes.controller.spi.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class NameUtilsTest {

    private static final String FIFTY_NINE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567";

    @Test
    void shouldShortenTheLabelAsExpected() {
        Map<String, String> map = Map.of("my-label", "my-label",
                "my-label-", "my-label",
                "my-very-very-very-very-very-very-very-very-very-very-very-long-label",
                "my-very-very-very-very-very-very-very-very-very-very-very-long",
                "my-very-very-very-very-very-very-very-very-very-very-very-long_label",
                "my-very-very-very-very-very-very-very-very-very-very-very-long",
                "my-very-very-very-very-very-very-very-very-very-very-very-long.label",
                "my-very-very-very-very-very-very-very-very-very-very-very-long",
                "my-very-very-very-very-very-very-very-very-very-very-very-looong-label",
                "my-very-very-very-very-very-very-very-very-very-very-very-looon");
        map.forEach((key, value) -> {
            String shortened = NameUtils.shortenLabelToMaxLength(key);
            assertEquals(shortened, value);
        });
    }

    @Test
    void testShortenTo63Chars() {
        Set<String> existing = new HashSet<>();
        //We are unlikely to have more than 10 resources with similar names in a namespace
        for (int i = 0; i < 10; i++) {
            String found = NameUtils.shortenToMaxLength(FIFTY_NINE_CHARS + "asdfasdfasdfasdfasdfasdfasdfasdfasdf");
            assertThat(found, startsWith(FIFTY_NINE_CHARS));
            assertThat(found.length(), is(63));
            char[] suffix = found.substring(59).toCharArray();
            for (char c : suffix) {
                assertTrue(Character.isDigit(c));
            }
            assertFalse(existing.contains(found));
            existing.add(found);
        }
    }

    @Test
    void testCamelCaseToDashDelimited() {
        assertThat(NameUtils.camelCaseToDashDelimited("EntandoCompositeAppController"), is("entando-composite-app-controller"));
        assertThat(NameUtils.camelCaseToDashDelimited("entandoCompositeAppController"), is("entando-composite-app-controller"));
        assertThat(NameUtils.camelCaseToDashDelimited("entandoComposite-appController"), is("entando-composite-app-controller"));
    }

    @Test
    void testControllerNameOf() {
        EntandoApp entandoApp = new EntandoApp();
        entandoApp.setKind("EntandoApp");
        assertThat(NameUtils.controllerNameOf(entandoApp), is("entando-k8s-app-controller"));
        EntandoDatabaseService entandoDatabaseService = new EntandoDatabaseService();
        entandoApp.setKind("EntandoDatabaseService");
        assertThat(NameUtils.controllerNameOf(entandoDatabaseService), is("entando-k8s-database-service-controller"));
    }

    @Test
    void shouldReturnARandomNumricStringOfTheExpectedLength() {
        IntStream.range(1, 10).forEach(i -> {
            String random = NameUtils.randomNumeric(i);
            assertThat(random.length(), is(i));
            assertThat(Integer.parseInt(random), greaterThanOrEqualTo(0));
        });
    }

    @Test
    void shouldGenerateADatabaseCompliantName() throws IOException {
        YAMLMapper yamlMapper = new YAMLMapper();
        final var entandoPlugin = yamlMapper.readValue(new File("src/test/resources/plugin-with-name.yml"),
                EntandoPlugin.class);

        // mysql
        entandoPlugin.getMetadata().setName("very-very-long-plugin-name");
        String dbName = NameUtils.databaseCompliantName(entandoPlugin, "qualifier", DbmsVendorConfig.MYSQL);
        assertThat(dbName, startsWith("very_very_long_plugin_name_qu"));
        assertThat(Integer.parseInt(dbName.substring(dbName.length() - 3)), greaterThanOrEqualTo(0));

        dbName = NameUtils.databaseCompliantName(entandoPlugin, null, DbmsVendorConfig.MYSQL);
        assertThat(dbName, is("very_very_long_plugin_name"));

        // postgres
        entandoPlugin.getMetadata().setName("very-very-very-very-very-very-very-very-long-plugin-name");
        dbName = NameUtils.databaseCompliantName(entandoPlugin, "qualifier", DbmsVendorConfig.POSTGRESQL);
        assertThat(dbName, startsWith("very_very_very_very_very_very_very_very_long_plugin_name_qua"));
        assertThat(Integer.parseInt(dbName.substring(dbName.length() - 3)), greaterThanOrEqualTo(0));

        dbName = NameUtils.databaseCompliantName(entandoPlugin, null, DbmsVendorConfig.POSTGRESQL);
        assertThat(dbName, is("very_very_very_very_very_very_very_very_long_plugin_name"));

        // oracle
        entandoPlugin.getMetadata().setName("very-very-very-very-very-very-very-very-very-very-very-very-very-very-very"
                + "-very-very-very-very-very-very-long-plugin-name");
        dbName = NameUtils.databaseCompliantName(entandoPlugin, "qualifier", DbmsVendorConfig.ORACLE);
        assertThat(dbName, startsWith("very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_"
                + "very_very_very_very_very_long_plugin_name_qua"));
        assertThat(Integer.parseInt(dbName.substring(dbName.length() - 3)), greaterThanOrEqualTo(0));

        dbName = NameUtils.databaseCompliantName(entandoPlugin, null, DbmsVendorConfig.ORACLE);
        System.out.println(dbName);
        assertThat(dbName, is("very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_ve"
                + "ry_very_very_very_very_long_plugin_name"));
    }

    @Test
    void shouldGenerateAStandardServiceName() throws IOException {

        YAMLMapper yamlMapper = new YAMLMapper();
        final var entandoPlugin = yamlMapper.readValue(new File("src/test/resources/plugin-with-name.yml"),
                EntandoPlugin.class);

        String serviceName = NameUtils.standardServiceName(entandoPlugin, NameUtils.MAIN_QUALIFIER);
        assertThat(serviceName, is("test-plugin-a-service"));

        serviceName = NameUtils.standardServiceName(entandoPlugin, "qualif");
        assertThat(serviceName, is("test-plugin-a-qualif-service"));

        serviceName = NameUtils.standardServiceName(entandoPlugin, "");
        assertThat(serviceName, is("test-plugin-a-service"));

        serviceName = NameUtils.standardServiceName(entandoPlugin, null);
        assertThat(serviceName, is("test-plugin-a-service"));
    }

    @Test
    void shouldLowerDashDelimited() {
        Map<String, String> map = Map.of(
                "simpleterm", "simpleterm",
                "SIMpleterm", "simpleterm",
                "dash-term", "dash-term",
                "DASH-term", "dash-term",
                "underscore_term", "underscore-term",
                "UNDERscore_term", "underscore-term");

        map.forEach((key, value) -> {
            final String actual = NameUtils.lowerDashDelimitedOf(key);
            assertThat(actual, is(value));
        });
    }
}
