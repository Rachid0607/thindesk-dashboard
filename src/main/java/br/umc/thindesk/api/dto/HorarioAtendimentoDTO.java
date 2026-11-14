package br.umc.thindesk.api.dto;

import br.umc.thindesk.horario.HorarioAtendimento;

public record HorarioAtendimentoDTO(String id, String setor, String diaSemana, String horarioInicio, String horarioFim) {

	public static HorarioAtendimentoDTO de(HorarioAtendimento h) {
		return new HorarioAtendimentoDTO(h.getId(), h.getSetor(), h.getDiaSemana().name(), h.getHorarioInicio(),
				h.getHorarioFim());
	}

}
