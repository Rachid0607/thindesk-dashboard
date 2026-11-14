package br.umc.thindesk.comum;

/**
 * Parametro de consulta invalido (vira HTTP 400 na API).
 */
public class ParametroInvalidoException extends RuntimeException {

	private final String parametro;

	public ParametroInvalidoException(String parametro, String mensagem) {
		super(mensagem);
		this.parametro = parametro;
	}

	public String getParametro() {
		return parametro;
	}

}
