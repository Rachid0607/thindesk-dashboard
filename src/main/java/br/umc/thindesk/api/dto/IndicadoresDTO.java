package br.umc.thindesk.api.dto;

import java.time.LocalDate;

/**
 * RF01 - indicadores gerais do periodo filtrado.
 * Tempos em horas (com uma casa decimal); percentuais de 0 a 100.
 * Campos de media/percentual sao nulos quando nao ha base de calculo.
 * abertosForaDoExpediente usa a grade de horarios de atendimento por setor.
 */
public record IndicadoresDTO(
		LocalDate inicio,
		LocalDate fim,
		long total,
		long abertos,
		long emAtendimento,
		long aguardandoSolicitante,
		long resolvidos,
		long fechados,
		long cancelados,
		long atrasados,
		Double percentualDentroSla,
		Double tempoMedioPrimeiroAtendimentoHoras,
		Double tempoMedioResolucaoHoras,
		Double taxaResolucaoPercentual,
		long totalPeriodoAnterior,
		Double variacaoTotalPercentual,
		long abertosForaDoExpediente) {
}
