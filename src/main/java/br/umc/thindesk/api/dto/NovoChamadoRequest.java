package br.umc.thindesk.api.dto;

import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.TipoChamado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST /api/v1/chamados.
 *
 * @param clienteId opcional (ADMINISTRADOR/TECNICO): abre o chamado em nome de
 *                  um cliente cadastrado; o setor passa a ser o do cliente
 * @param setor     obrigatorio quando nao ha clienteId
 */
public record NovoChamadoRequest(
		@NotBlank(message = "Informe o título.") @Size(min = 5, max = 120, message = "O título deve ter entre 5 e 120 caracteres.") String titulo,
		@NotBlank(message = "Descreva o problema.") @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.") String descricao,
		@NotNull(message = "Informe o tipo.") TipoChamado tipo,
		@NotNull(message = "Informe a prioridade.") Prioridade prioridade,
		@Size(max = 60, message = "O setor deve ter no máximo 60 caracteres.") String setor,
		String clienteId) {
}
