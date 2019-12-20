package org.entando.kubernetes.controller.inprocesstest;

import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PodClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

@Tag("in-process")
public class PublicIngressingMockClientTest extends PublicIngressingTestBase {

    SimpleK8SClientDouble simpleK8SClientDouble = new SimpleK8SClientDouble();

    @BeforeAll
    public static void emulatePodWaiting() {
        PodClientDouble.setEmulatePodWatching(true);
    }

    @Override
    public SimpleK8SClient getClient() {
        return simpleK8SClientDouble;
    }

    @AfterAll
    public static void dontEmulatePodWaiting() {
        PodClientDouble.setEmulatePodWatching(false);
    }

}
