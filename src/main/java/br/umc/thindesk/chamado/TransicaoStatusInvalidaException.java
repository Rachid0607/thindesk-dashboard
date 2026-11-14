package br.umc.thindesk.chamado;

/**
 * Mudanca de status nao permitida pela maquina de estados (vira HTTP 409 na API).
 */
public class TransicaoStatusInvalidaException extends RuntimeException {

	private final StatusChamado atual;
	private final StatusChamado destino;

	public TransicaoStatusInvalidaException(StatusChamado atual, StatusChamado destino) {
		super("Não é possível mudar o chamado de " + atual + " para " + destino
				+ ". Destinos permitidos a partir de " + atual + ": " + atual.proximosPermitidos() + ".");
		this.atual = atual;
		this.destino = destino;
	}

	public StatusChamado getAtual() {
		return atual;
	}

	public StatusChamado getDestino() {
		return destino;
	}

}
