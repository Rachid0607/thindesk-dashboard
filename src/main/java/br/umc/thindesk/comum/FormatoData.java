package br.umc.thindesk.comum;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

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

	/** Nome do dia da semana em portugues, com inicial maiuscula (ex.: "Segunda-feira"). */
	public String diaSemana(DayOfWeek dia) {
		if (dia == null) {
			return "-";
		}
		String nome = dia.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
		return Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
	}

}
