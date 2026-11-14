package br.umc.thindesk.dashboard;

/**
 * Granularidade da serie temporal do grafico de atendimentos.
 */
public enum Agrupamento {

	DIA("day"),
	SEMANA("week"),
	MES("month");

	private final String unidadeMongo;

	Agrupamento(String unidadeMongo) {
		this.unidadeMongo = unidadeMongo;
	}

	/** Unidade usada no operador $dateTrunc do MongoDB. */
	public String getUnidadeMongo() {
		return unidadeMongo;
	}

}
