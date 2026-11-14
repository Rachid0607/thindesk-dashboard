package br.umc.thindesk.api.dto;

import java.time.Instant;
import java.util.List;

import br.umc.thindesk.chamado.Chamado;
import br.umc.thindesk.chamado.Pessoa;
import br.umc.thindesk.chamado.RegraSla;

/**
 * Chamado completo, com consideracoes tecnicas, historico de status e os
 * proximos status permitidos pela maquina de estados.
 */
public record ChamadoDetalheDTO(String id, String protocolo, String titulo, String descricao, String tipo,
		String tipoDescricao, String prioridade, String prioridadeDescricao, int slaHoras, String status,
		String statusDescricao, String setor, Pessoa solicitante, Pessoa tecnico, Instant dataAbertura,
		Instant dataInicioAtendimento, Instant dataResolucao, Instant dataFechamento, Instant prazoSla,
		boolean atrasado, List<String> proximosStatusPermitidos, List<Consideracao> consideracoes,
		List<Mudanca> historico) {

	public record Consideracao(String texto, String autor, Instant data) {
	}

	public record Mudanca(String de, String para, String usuario, Instant data) {
	}

	public static ChamadoDetalheDTO de(Chamado c, Instant agora) {
		return new ChamadoDetalheDTO(c.getId(), c.getProtocolo(), c.getTitulo(), c.getDescricao(), c.getTipo().name(),
				c.getTipo().getDescricao(), c.getPrioridade().name(), c.getPrioridade().getDescricao(),
				c.getPrioridade().getSlaHoras(), c.getStatus().name(), c.getStatus().getDescricao(), c.getSetor(),
				c.getSolicitante(), c.getTecnico(), c.getDataAbertura(), c.getDataInicioAtendimento(),
				c.getDataResolucao(), c.getDataFechamento(), c.getPrazoSla(), RegraSla.estaAtrasado(c, agora),
				c.getStatus().proximosPermitidos().stream().map(Enum::name).toList(),
				c.getConsideracoes().stream()
						.map(x -> new Consideracao(x.texto(), x.autor() == null ? null : x.autor().nome(), x.data()))
						.toList(),
				c.getHistorico().stream()
						.map(h -> new Mudanca(h.de() == null ? null : h.de().name(), h.para().name(),
								h.usuario() == null ? null : h.usuario().nome(), h.data()))
						.toList());
	}

}
