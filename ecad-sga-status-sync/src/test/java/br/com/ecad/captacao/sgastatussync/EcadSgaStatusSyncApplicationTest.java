package br.com.ecad.captacao.sgastatussync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = EcadSgaStatusSyncApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "SGA_VERIFICATION_ENABLED=false")
class EcadSgaStatusSyncApplicationTest {
    @Test
    void contextLoads() {
    }
}
