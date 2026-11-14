package br.umc.thindesk.horario;

import java.time.DayOfWeek;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class HorarioForm {

	@NotBlank(message = "Informe o setor.")
	@Size(max = 60, message = "O setor deve ter no máximo 60 caracteres.")
	private String setor;

	@NotNull(message = "Selecione o dia da semana.")
	private DayOfWeek diaSemana;

	@NotBlank(message = "Informe o início.")
	@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use o formato HH:mm.")
	private String horarioInicio;

	@NotBlank(message = "Informe o fim.")
	@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use o formato HH:mm.")
	private String horarioFim;

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
