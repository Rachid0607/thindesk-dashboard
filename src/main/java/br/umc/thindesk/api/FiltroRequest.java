package br.umc.thindesk.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.StatusChamado;
import br.umc.thindesk.chamado.TipoChamado;

/**
 * Parametros de filtro aceitos pelos endpoints do dashboard e da listagem de
 * chamados (todos opcionais). Listas aceitam valores separados por virgula
 * (ex.: status=ABERTO,EM_ATENDIMENTO) ou o parametro repetido.
 */
public class FiltroRequest {

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate inicio;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate fim;

	private List<TipoChamado> tipo;

	private List<StatusChamado> status;

	private List<Prioridade> prioridade;

	private String tecnicoId;

	private String setor;

	public LocalDate getInicio() {
		return inicio;
	}

	public void setInicio(LocalDate inicio) {
		this.inicio = inicio;
	}

	public LocalDate getFim() {
		return fim;
	}

	public void setFim(LocalDate fim) {
		this.fim = fim;
	}

	public List<TipoChamado> getTipo() {
		return tipo;
	}

	public void setTipo(List<TipoChamado> tipo) {
		this.tipo = tipo;
	}

	public List<StatusChamado> getStatus() {
		return status;
	}

	public void setStatus(List<StatusChamado> status) {
		this.status = status;
	}

	public List<Prioridade> getPrioridade() {
		return prioridade;
	}

	public void setPrioridade(List<Prioridade> prioridade) {
		this.prioridade = prioridade;
	}

	public String getTecnicoId() {
		return tecnicoId;
	}

	public void setTecnicoId(String tecnicoId) {
		this.tecnicoId = tecnicoId;
	}

	public String getSetor() {
		return setor;
	}

	public void setSetor(String setor) {
		this.setor = setor;
	}

}
