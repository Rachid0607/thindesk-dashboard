package br.umc.thindesk.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * RF02 - abertos x resolvidos por periodo. Periodos sem chamados aparecem com zero.
 */
public record SerieTemporalDTO(LocalDate inicio, LocalDate fim, String agrupamento, List<Ponto> pontos) {

	/**
	 * @param periodo data de inicio do periodo (dia, segunda-feira da semana ou dia 1 do mes)
	 * @param rotulo  texto curto para o eixo do grafico
	 */
	public record Ponto(LocalDate periodo, String rotulo, long abertos, long resolvidos) {
	}

}
