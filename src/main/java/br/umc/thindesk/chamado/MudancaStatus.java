package br.umc.thindesk.chamado;

import java.time.Instant;

/**
 * Item do historico de status do chamado.
 */
public record MudancaStatus(StatusChamado de, StatusChamado para, Pessoa usuario, Instant data) {
}
