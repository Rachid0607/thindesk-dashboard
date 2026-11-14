package br.umc.thindesk.api.dto;

import java.util.List;

/**
 * Pagina de resultados (pagina comeca em 0).
 */
public record PaginaDTO<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {
}
