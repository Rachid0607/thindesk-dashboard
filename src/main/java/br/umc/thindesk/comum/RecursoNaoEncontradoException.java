package br.umc.thindesk.comum;

/**
 * Recurso pedido nao existe (vira HTTP 404 na API).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

	public RecursoNaoEncontradoException(String mensagem) {
		super(mensagem);
	}

}
