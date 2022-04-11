package org.entando.kubernetes.controller.spi.container;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Map;
import java.util.regex.Pattern;
import org.bouncycastle.util.encoders.Base64;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DbAwareContainerTest {

    private final Pattern finalUsernameRegex = Pattern.compile("_(\\w|\\d){5}$");

    @Test
    void shouldGenerateTheExpectedUsername() {
        Map<String, String> mysqlMap = Map.of(
                "myschema", "myschema",
                "mylooooooooooooooooooooooongnameohmygod", "mylooooooooooooooooooooooongname");
        verifyDbMapUsernameGeneration(mysqlMap, DbmsVendorConfig.MYSQL);

        Map<String, String> postgresMap = Map.of(
                "myschema", "myschema",
                "mylooooongnameohmygod", "mylooooongnameohmygod",
                "mylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygod",
                "mylooooongnameohmygodmylooooongnameohmygodmylooooongnameoh");
        verifyDbMapUsernameGeneration(postgresMap, DbmsVendorConfig.POSTGRESQL);

        Map<String, String> oracleMap = Map.of(
                "myschema", "myschema",
                "mylooooongnameohmygod", "mylooooongnameohmygod",
                "mylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygod",
                "mylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygod",
                "mylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongname"
                        + "ohmygodmylooooongnameohmygodmylooooongnameohmygod",
                "mylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongnameohmygodmylooooongname"
                        + "ohmygodmylooooongnameohmygod");
        verifyDbMapUsernameGeneration(oracleMap, DbmsVendorConfig.ORACLE);
    }

    private void verifyDbMapUsernameGeneration(Map<String, String> map, DbmsVendorConfig vendorConfig) {
        map.forEach((key, value) -> {
            final String username = DbAwareContainer.generateUsername(key, vendorConfig);
            String prefix = username.substring(0, username.length() - 6);
            assertTrue(value.startsWith(prefix));
            assertTrue(finalUsernameRegex.matcher(username).find());
            assertTrue(username.length() <= vendorConfig.getMaxUsernameLength());
        });
    }

    @Test
    void shouldGetUsernameFromSecretWhilePresent() {

        String secretName = "my-secret";
        String schemaName = "my-schema";
        String expected = "my-username";
        String encoded = new String(Base64.encode(expected.getBytes()));

        Secret secret = mock(Secret.class, Mockito.RETURNS_DEEP_STUBS);
        when(secret.getData().get("username")).thenReturn(encoded);

        SecretClient secretClient = mock(SecretClient.class);
        when(secretClient.loadControllerSecret(anyString())).thenReturn(secret);

        final String username = DbAwareContainer.getUsername(secretClient, secretName, schemaName,
                DbmsVendorConfig.MYSQL);

        assertEquals(expected, username);
    }

    @Test
    void shouldGenerateRandomUsernameWhileSecretNotPresent() {

        String secretName = "my-secret";
        String schemaName = "my-schema";

        SecretClient secretClient = mock(SecretClient.class);
        when(secretClient.loadControllerSecret(anyString())).thenReturn(null);

        final String username = DbAwareContainer.getUsername(secretClient, secretName, schemaName,
                DbmsVendorConfig.MYSQL);

        assertNotNull(username);
        assertTrue(username.startsWith(schemaName + "_"));
        assertDoesNotThrow(() -> Integer.parseInt(username.substring(username.length() - 5)));
    }
}
