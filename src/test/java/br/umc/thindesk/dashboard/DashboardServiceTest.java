package br.umc.thindesk.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.horario.HorarioAtendimentoService;
import br.umc.thindesk.usuario.UsuarioRepository;

@DisplayName("Cálculos auxiliares do dashboard")
class DashboardServiceTest {

	@Test
	void inicioDoPeriodoPorAgrupamento() {
		LocalDate quinta = LocalDate.of(2026, 11, 12);
		assertThat(DashboardService.inicioDoPeriodo(quinta, Agrupamento.DIA)).isEqualTo(quinta);
		assertThat(DashboardService.inicioDoPeriodo(quinta, Agrupamento.SEMANA)).isEqualTo(LocalDate.of(2026, 11, 9));
		assertThat(DashboardService.inicioDoPeriodo(quinta, Agrupamento.MES)).isEqualTo(LocalDate.of(2026, 11, 1));
	}

	@Test
	void rotulosDoEixo() {
		LocalDate dia = LocalDate.of(2026, 11, 9);
		assertThat(DashboardService.rotulo(dia, Agrupamento.DIA)).isEqualTo("09/11");
		assertThat(DashboardService.rotulo(dia, Agrupamento.SEMANA)).isEqualTo("sem. 09/11");
		assertThat(DashboardService.rotulo(dia, Agrupamento.MES)).isEqualTo("nov/26");
	}

	@Test
	void percentualSemBaseEhNulo() {
		assertThat(DashboardService.percentual(1, 0)).isNull();
		assertThat(DashboardService.percentual(1, 3)).isEqualTo(33.3);
	}

	@Test
	void limiteForaDaFaixaEhRecusadoAntesDeConsultarOBanco() {
		DashboardService service = new DashboardService(mock(MongoTemplate.class), mock(UsuarioRepository.class),
				mock(HorarioAtendimentoService.class), Clock.systemUTC());
		FiltroChamados filtro = new FiltroChamados(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), null, null,
				null, null, null);
		assertThatThrownBy(() -> service.tiposMaisAtendidos(filtro, 0)).isInstanceOf(ParametroInvalidoException.class);
		assertThatThrownBy(() -> service.rankingTecnicos(filtro, 21)).isInstanceOf(ParametroInvalidoException.class);
	}

}
