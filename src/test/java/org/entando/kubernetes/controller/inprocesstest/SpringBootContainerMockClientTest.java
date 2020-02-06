package org.entando.kubernetes.controller.inprocesstest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PodClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

@Tag("in-process")
public class SpringBootContainerMockClientTest extends SpringBootContainerTestBase {

    private SimpleK8SClientDouble simpleK8SClientDouble = new SimpleK8SClientDouble();
    private SimpleKeycloakClient keycloakClient = Mockito.mock(SimpleKeycloakClient.class);

    @BeforeAll
    public static void emulatePodWaiting() {
        PodClientDouble.setEmulatePodWatching(true);

    }

    @AfterAll
    public static void dontEmulatePodWaiting() {
        PodClientDouble.setEmulatePodWatching(false);
    }

    @BeforeEach
    public void prepareKeycloakMocks() {
        lenient().when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn("ASDFASDFASDfa");

    }

    @Override
    public SimpleK8SClient getClient() {
        return simpleK8SClientDouble;
    }

    @Override
    protected SimpleKeycloakClient getKeycloakClient() {
        return keycloakClient;
    }
}