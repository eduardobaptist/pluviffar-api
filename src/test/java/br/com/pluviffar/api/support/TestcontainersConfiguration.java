package br.com.pluviffar.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * TimescaleDB efêmero para os testes: mesma versão e edição (Apache/"oss") da produção.
 * Os testes não tocam no banco do docker compose.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String IMAGE = "timescale/timescaledb:2.25.1-pg18-oss";

    @Bean
    @ServiceConnection
    PostgreSQLContainer timescaleContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
