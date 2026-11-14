package br.umc.thindesk.chamado;

/**
 * Categoria do chamado. A descricao e o texto exibido no dashboard.
 */
public enum TipoChamado {

	HARDWARE("Hardware"),
	SOFTWARE("Software"),
	REDE("Rede e internet"),
	ACESSO_SENHA("Acesso e senha"),
	EMAIL("E-mail"),
	IMPRESSORA("Impressora"),
	SISTEMA_INTERNO("Sistema interno"),
	OUTROS("Outros");

	private final String descricao;

	TipoChamado(String descricao) {
		this.descricao = descricao;
	}

	public String getDescricao() {
		return descricao;
	}

}
