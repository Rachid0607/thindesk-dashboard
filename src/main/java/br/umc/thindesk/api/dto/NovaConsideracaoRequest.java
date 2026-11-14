package br.umc.thindesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST /api/v1/chamados/{id}/consideracoes.
 */
public record NovaConsideracaoRequest(
		@NotBlank(message = "Informe o texto da consideração.") @Size(max = 1000, message = "A consideração deve ter no máximo 1000 caracteres.") String texto) {
}
