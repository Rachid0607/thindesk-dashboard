package br.umc.thindesk.chamado;

import java.time.Instant;

/**
 * Anotacao tecnica registrada no chamado (diagnostico, solucao aplicada,
 * pendencias).
 */
public record ConsideracaoTecnica(String texto, Pessoa autor, Instant data) {
}
