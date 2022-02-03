package org.entando.kubernetes.controller.spi.container;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.regex.Pattern;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DbAwareContainerTest {

    private final Pattern finalUsernameRegex = Pattern.compile("_(\\w|\\d){5}$");

    @Test
    void shouldGenerateTheExpectedUsername() {
        Map<String, String> mysqlMap = Map.of(
                "myschema", "myschema",
                "mylooooongnameohmygod", "mylooooong");
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

}
