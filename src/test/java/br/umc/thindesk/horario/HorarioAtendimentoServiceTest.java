package br.umc.thindesk.horario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.umc.thindesk.comum.RegraNegocioException;

@DisplayName("Horário de atendimento")
class HorarioAtendimentoServiceTest {

	private static final Map<String, Map<DayOfWeek, HorarioAtendimento>> GRADE = Map.of("Financeiro",
			Map.of(DayOfWeek.MONDAY, new HorarioAtendimento("Financeiro", DayOfWeek.MONDAY, "08:00", "18:00")));

	@Test
	void dentroDoHorarioDoDia() {
		// segunda-feira 16/11/2026, 10:00 em Sao Paulo = 13:00 UTC
		assertThat(HorarioAtendimentoService.dentroDoExpediente(GRADE, "Financeiro",
				Instant.parse("2026-11-16T13:00:00Z"))).isTrue();
	}

	@Test
	void foraDoHorarioOuDoDia() {
		// segunda 19:00 em Sao Paulo
		assertThat(HorarioAtendimentoService.dentroDoExpediente(GRADE, "Financeiro",
				Instant.parse("2026-11-16T22:00:00Z"))).isFalse();
		// fim do expediente e exclusivo: 18:00 ja esta fora
		assertThat(HorarioAtendimentoService.dentroDoExpediente(GRADE, "Financeiro",
				Instant.parse("2026-11-16T21:00:00Z"))).isFalse();
		// terca nao tem horario cadastrado para o setor
		assertThat(HorarioAtendimentoService.dentroDoExpediente(GRADE, "Financeiro",
				Instant.parse("2026-11-17T13:00:00Z"))).isFalse();
	}

	@Test
	void setorSemGradeNaoTemRestricao() {
		assertThat(HorarioAtendimentoService.dentroDoExpediente(GRADE, "Jurídico",
				Instant.parse("2026-11-15T03:00:00Z"))).isTrue();
	}

	@Test
	void inicioDepoisDoFimEhRecusado() {
		HorarioAtendimentoService service = new HorarioAtendimentoService(mock(HorarioAtendimentoRepository.class));
		HorarioForm form = new HorarioForm();
		form.setSetor("Financeiro");
		form.setDiaSemana(DayOfWeek.MONDAY);
		form.setHorarioInicio("18:00");
		form.setHorarioFim("08:00");
		assertThatThrownBy(() -> service.salvar(form)).isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void salvarMesmoSetorEDiaSubstituiOHorario() {
		HorarioAtendimentoRepository repo = mock(HorarioAtendimentoRepository.class);
		HorarioAtendimento existente = new HorarioAtendimento("Financeiro", DayOfWeek.MONDAY, "08:00", "18:00");
		existente.setId("h1");
		when(repo.findBySetorAndDiaSemana("Financeiro", DayOfWeek.MONDAY)).thenReturn(Optional.of(existente));
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		HorarioForm form = new HorarioForm();
		form.setSetor(" Financeiro ");
		form.setDiaSemana(DayOfWeek.MONDAY);
		form.setHorarioInicio("09:00");
		form.setHorarioFim("17:00");

		HorarioAtendimento salvo = new HorarioAtendimentoService(repo).salvar(form);

		assertThat(salvo.getId()).isEqualTo("h1");
		assertThat(salvo.getHorarioInicio()).isEqualTo("09:00");
	}

}
