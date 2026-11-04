package br.com.sentinela;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
// Sobe o Postgres em container: so roda onde houver Docker (CI ou maquina com Docker).
@Testcontainers(disabledWithoutDocker = true)
class SentinelaApplicationTests {

    @Test
    void contextLoads() {
    }
}
