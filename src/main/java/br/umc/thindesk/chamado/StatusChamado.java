package br.umc.thindesk.chamado;

import java.util.EnumSet;
import java.util.Set;

/**
 * Situacao do chamado e a maquina de estados que define quais mudancas
 * sao permitidas. FECHADO e CANCELADO sao estados finais.
 *
 * <pre>
 * ABERTO                 -> EM_ATENDIMENTO, CANCELADO
 * EM_ATENDIMENTO         -> AGUARDANDO_SOLICITANTE, RESOLVIDO, CANCELADO
 * AGUARDANDO_SOLICITANTE -> EM_ATENDIMENTO, CANCELADO
 * RESOLVIDO              -> FECHADO, EM_ATENDIMENTO (reabertura)
 * </pre>
 */
public enum StatusChamado {

	ABERTO("Aberto"),
	EM_ATENDIMENTO("Em atendimento"),
	AGUARDANDO_SOLICITANTE("Aguardando solicitante"),
	RESOLVIDO("Resolvido"),
	FECHADO("Fechado"),
	CANCELADO("Cancelado");

	private final String descricao;

	StatusChamado(String descricao) {
		this.descricao = descricao;
	}

	public String getDescricao() {
		return descricao;
	}

	public Set<StatusChamado> proximosPermitidos() {
		return switch (this) {
			case ABERTO -> EnumSet.of(EM_ATENDIMENTO, CANCELADO);
			case EM_ATENDIMENTO -> EnumSet.of(AGUARDANDO_SOLICITANTE, RESOLVIDO, CANCELADO);
			case AGUARDANDO_SOLICITANTE -> EnumSet.of(EM_ATENDIMENTO, CANCELADO);
			case RESOLVIDO -> EnumSet.of(FECHADO, EM_ATENDIMENTO);
			case FECHADO, CANCELADO -> EnumSet.noneOf(StatusChamado.class);
		};
	}

	public boolean podeIrPara(StatusChamado destino) {
		return destino != null && proximosPermitidos().contains(destino);
	}

	public boolean isFinal() {
		return proximosPermitidos().isEmpty();
	}

	/** Chamado ja atendido (resolvido ou fechado), usado nos indicadores. */
	public boolean isAtendido() {
		return this == RESOLVIDO || this == FECHADO;
	}

}
