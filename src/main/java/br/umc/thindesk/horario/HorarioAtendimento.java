package br.umc.thindesk.horario;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Horario de atendimento de um setor em um dia da semana (vem do Thindesk
 * original, colecao "horariosAtendimento"). Os horarios ficam como texto
 * "HH:mm", igual ao sistema original.
 */
@Document(collection = "horariosAtendimento")
@CompoundIndex(name = "idx_setor_dia", def = "{'setor': 1, 'diaSemana': 1}", unique = true)
public class HorarioAtendimento {

	@Id
	private String id;

	private String setor;

	private DayOfWeek diaSemana;

	private String horarioInicio;

	private String horarioFim;

	public HorarioAtendimento() {
	}

	public HorarioAtendimento(String setor, DayOfWeek diaSemana, String horarioInicio, String horarioFim) {
		this.setor = setor;
		this.diaSemana = diaSemana;
		this.horarioInicio = horarioInicio;
		this.horarioFim = horarioFim;
	}

	/** Se o horario informado (no fuso de Sao Paulo) esta dentro do expediente. */
	public boolean cobre(LocalTime horario) {
		return !horario.isBefore(LocalTime.parse(horarioInicio)) && horario.isBefore(LocalTime.parse(horarioFim));
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSetor() {
		return setor;
	}

	public void setSetor(String setor) {
		this.setor = setor;
	}

	public DayOfWeek getDiaSemana() {
		return diaSemana;
	}

	public void setDiaSemana(DayOfWeek diaSemana) {
		this.diaSemana = diaSemana;
	}

	public String getHorarioInicio() {
		return horarioInicio;
	}

	public void setHorarioInicio(String horarioInicio) {
		this.horarioInicio = horarioInicio;
	}

	public String getHorarioFim() {
		return horarioFim;
	}

	public void setHorarioFim(String horarioFim) {
		this.horarioFim = horarioFim;
	}

}
