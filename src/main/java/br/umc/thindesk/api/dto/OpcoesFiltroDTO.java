package br.umc.thindesk.api.dto;

import java.util.List;

/**
 * RF05 - valores disponiveis para popular os campos de filtro do dashboard.
 */
public record OpcoesFiltroDTO(List<Opcao> tipos, List<Opcao> status, List<Opcao> prioridades, List<Opcao> tecnicos,
		List<String> setores, List<Opcao> agrupamentos) {

	public record Opcao(String codigo, String descricao) {
	}

}
