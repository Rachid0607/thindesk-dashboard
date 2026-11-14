package br.umc.thindesk.api.dto;

import java.time.Instant;

import br.umc.thindesk.chamado.Chamado;
import br.umc.thindesk.chamado.RegraSla;

/**
 * Linha da listagem de chamados.
 */
public record ChamadoResumoDTO(String id, String protocolo, String titulo, String tipo, String tipoDescricao,
		String prioridade, String prioridadeDescricao, String status, String statusDescricao, String setor,
		String solicitante, String tecnico, Instant dataAbertura, Instant prazoSla, Instant dataResolucao,
		boolean atrasado) {

	public static ChamadoResumoDTO de(Chamado c, Instant agora) {
		return new ChamadoResumoDTO(c.getId(), c.getProtocolo(), c.getTitulo(), c.getTipo().name(),
				c.getTipo().getDescricao(), c.getPrioridade().name(), c.getPrioridade().getDescricao(),
				c.getStatus().name(), c.getStatus().getDescricao(), c.getSetor(),
				c.getSolicitante() == null ? null : c.getSolicitante().nome(),
				c.getTecnico() == null ? null : c.getTecnico().nome(), c.getDataAbertura(), c.getPrazoSla(),
				c.getDataResolucao(), RegraSla.estaAtrasado(c, agora));
	}

}
