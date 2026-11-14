package br.umc.thindesk.api.dto;

import java.util.List;

/**
 * RF03 - quantidade de chamados por status e por prioridade.
 */
public record SituacaoDTO(long total, List<Item> porStatus, List<Item> porPrioridade) {

	public record Item(String codigo, String descricao, long quantidade, double percentual) {
	}

}
