package br.umc.thindesk.api.dto;

/**
 * Ranking de tecnicos por chamados atendidos no periodo.
 */
public record TecnicoRankingDTO(int posicao, String tecnicoId, String nome, long atendidos, long emAndamento,
		Double tempoMedioResolucaoHoras, Double percentualDentroSla) {
}
