package br.umc.thindesk.chamado;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Chamado tecnico. Solicitante, tecnico, consideracoes e historico ficam
 * embutidos no proprio documento: sao sempre lidos junto com o chamado e
 * evitam consultas extras nas agregacoes do dashboard.
 */
@Document(collection = "chamados")
@CompoundIndexes({
	@CompoundIndex(name = "idx_tecnico", def = "{'tecnico.usuarioId': 1}"),
	@CompoundIndex(name = "idx_solicitante", def = "{'solicitante.usuarioId': 1}"),
	@CompoundIndex(name = "idx_status_abertura", def = "{'status': 1, 'dataAbertura': -1}")
})
public class Chamado {

	@Id
	private String id;

	@Indexed(unique = true)
	private String protocolo;

	private String titulo;

	private String descricao;

	@Indexed
	private TipoChamado tipo;

	private Prioridade prioridade;

	@Indexed
	private StatusChamado status;

	@Indexed
	private String setor;

	private Pessoa solicitante;

	private Pessoa tecnico;

	@Indexed
	private Instant dataAbertura;

	private Instant dataInicioAtendimento;

	private Instant dataResolucao;

	private Instant dataFechamento;

	private Instant prazoSla;

	private List<ConsideracaoTecnica> consideracoes = new ArrayList<>();

	private List<MudancaStatus> historico = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProtocolo() {
		return protocolo;
	}

	public void setProtocolo(String protocolo) {
		this.protocolo = protocolo;
	}

	public String getTitulo() {
		return titulo;
	}

	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public TipoChamado getTipo() {
		return tipo;
	}

	public void setTipo(TipoChamado tipo) {
		this.tipo = tipo;
	}

	public Prioridade getPrioridade() {
		return prioridade;
	}

	public void setPrioridade(Prioridade prioridade) {
		this.prioridade = prioridade;
	}

	public StatusChamado getStatus() {
		return status;
	}

	public void setStatus(StatusChamado status) {
		this.status = status;
	}

	public String getSetor() {
		return setor;
	}

	public void setSetor(String setor) {
		this.setor = setor;
	}

	public Pessoa getSolicitante() {
		return solicitante;
	}

	public void setSolicitante(Pessoa solicitante) {
		this.solicitante = solicitante;
	}

	public Pessoa getTecnico() {
		return tecnico;
	}

	public void setTecnico(Pessoa tecnico) {
		this.tecnico = tecnico;
	}

	public Instant getDataAbertura() {
		return dataAbertura;
	}

	public void setDataAbertura(Instant dataAbertura) {
		this.dataAbertura = dataAbertura;
	}

	public Instant getDataInicioAtendimento() {
		return dataInicioAtendimento;
	}

	public void setDataInicioAtendimento(Instant dataInicioAtendimento) {
		this.dataInicioAtendimento = dataInicioAtendimento;
	}

	public Instant getDataResolucao() {
		return dataResolucao;
	}

	public void setDataResolucao(Instant dataResolucao) {
		this.dataResolucao = dataResolucao;
	}

	public Instant getDataFechamento() {
		return dataFechamento;
	}

	public void setDataFechamento(Instant dataFechamento) {
		this.dataFechamento = dataFechamento;
	}

	public Instant getPrazoSla() {
		return prazoSla;
	}

	public void setPrazoSla(Instant prazoSla) {
		this.prazoSla = prazoSla;
	}

	public List<ConsideracaoTecnica> getConsideracoes() {
		return consideracoes;
	}

	public void setConsideracoes(List<ConsideracaoTecnica> consideracoes) {
		this.consideracoes = consideracoes == null ? new ArrayList<>() : consideracoes;
	}

	public List<MudancaStatus> getHistorico() {
		return historico;
	}

	public void setHistorico(List<MudancaStatus> historico) {
		this.historico = historico == null ? new ArrayList<>() : historico;
	}

}
