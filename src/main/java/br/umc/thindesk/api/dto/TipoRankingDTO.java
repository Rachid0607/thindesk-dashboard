package br.umc.thindesk.api.dto;

/**
 * RF04 - item do ranking de tipos mais atendidos.
 *
 * @param atendidos  chamados RESOLVIDOS ou FECHADOS do tipo
 * @param percentual participacao do tipo no total de atendidos do periodo
 */
public record TipoRankingDTO(int posicao, String codigo, String descricao, long total, long atendidos,
		double percentual, Double tempoMedioResolucaoHoras) {
}
