package br.umc.thindesk.chamado;

/**
 * Referencia resumida a um usuario, embutida no chamado (usuarioId + nome; o nome do campo evita o mapeamento automatico de "id" para "_id"), para o
 * dashboard nao precisar de "join" com a colecao de usuarios.
 */
public record Pessoa(String usuarioId, String nome) {
}
