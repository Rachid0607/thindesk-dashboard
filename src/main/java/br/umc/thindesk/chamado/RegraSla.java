package br.umc.thindesk.chamado;

import java.time.Instant;

/**
 * Regras de prazo (SLA). O prazo e a data de abertura mais as horas de SLA da
 * prioridade. Um chamado esta atrasado quando:
 * - foi resolvido depois do prazo; ou
 * - ainda nao foi resolvido (e nao foi cancelado) e o prazo ja passou.
 */
public final class RegraSla {

	private RegraSla() {
	}

	public static Instant calcularPrazo(Prioridade prioridade, Instant abertura) {
		return abertura.plus(prioridade.getSla());
	}

	public static boolean estaAtrasado(Chamado chamado, Instant agora) {
		if (chamado.getPrazoSla() == null || chamado.getStatus() == StatusChamado.CANCELADO) {
			return false;
		}
		if (chamado.getDataResolucao() != null) {
			return chamado.getDataResolucao().isAfter(chamado.getPrazoSla());
		}
		return agora.isAfter(chamado.getPrazoSla());
	}

}
