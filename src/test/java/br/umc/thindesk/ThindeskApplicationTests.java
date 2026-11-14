package br.umc.thindesk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sobe o contexto completo da aplicacao. Precisa de um MongoDB de verdade,
 * por isso so roda quando MONGODB_URI esta definida.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
class ThindeskApplicationTests {

	@Test
	void contextLoads() {
	}

}
