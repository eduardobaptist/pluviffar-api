package br.com.pluviffar.api;

import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Sobe o contexto completo: Flyway (V1 + seed) e validação das entidades JPA contra o schema (ddl-auto=validate). */
class ApiApplicationTests extends IntegrationTest {

    @Test
    void contextLoads() {
    }
}
