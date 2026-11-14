package br.umc.thindesk.comum;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import br.umc.thindesk.config.RelogioConfig;

/**
 * Formata datas no fuso de Sao Paulo para as telas Thymeleaf
 * (uso: ${@formatoData.dataHora(valor)}). As datas sao gravadas em UTC.
 */
@Component("formatoData")
public class FormatoData {

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(RelogioConfig.FUSO);

	public String dataHora(Instant instante) {
		return instante == null ? "-" : DATA_HORA.format(instante);
	}

}
