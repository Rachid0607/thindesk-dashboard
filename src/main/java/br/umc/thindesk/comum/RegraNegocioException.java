package br.umc.thindesk.comum;

/**
 * Pedido bem formado, mas que viola uma regra de negocio (vira HTTP 422 na API).
 */
public class RegraNegocioException extends RuntimeException {

	public RegraNegocioException(String mensagem) {
		super(mensagem);
	}

}
