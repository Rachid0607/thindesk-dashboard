package br.umc.thindesk.chamado;

import java.time.Duration;

/**
 * Prioridade do chamado. Cada prioridade define o prazo de resolucao (SLA)
 * contado a partir da abertura.
 */
public enum Prioridade {

	BAIXA("Baixa", 72),
	MEDIA("Média", 48),
	ALTA("Alta", 24),
	CRITICA("Crítica", 8);

	private final String descricao;
	private final int slaHoras;

	Prioridade(String descricao, int slaHoras) {
		this.descricao = descricao;
		this.slaHoras = slaHoras;
	}

	public String getDescricao() {
		return descricao;
	}

	public int getSlaHoras() {
		return slaHoras;
	}

	public Duration getSla() {
		return Duration.ofHours(slaHoras);
	}

}
