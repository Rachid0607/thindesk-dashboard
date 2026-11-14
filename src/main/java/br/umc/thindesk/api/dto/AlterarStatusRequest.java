package br.umc.thindesk.api.dto;

import br.umc.thindesk.chamado.StatusChamado;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo do PATCH /api/v1/chamados/{id}/status.
 *
 * @param tecnicoId  opcional; atribui (ou troca) o tecnico responsavel
 * @param comentario opcional; vira uma consideracao tecnica
 */
public record AlterarStatusRequest(
		@NotNull(message = "Informe o novo status.") StatusChamado status,
		String tecnicoId,
		@Size(max = 1000, message = "O comentário deve ter no máximo 1000 caracteres.") String comentario) {
}
