package br.umc.thindesk.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relogio unico da aplicacao. Injetar o Clock (em vez de chamar Instant.now()
 * direto) deixa as regras de SLA e os indicadores testaveis com data fixa.
 */
@Configuration
public class RelogioConfig {

	/** Fuso usado para interpretar datas de filtro e agrupar os graficos. */
	public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
