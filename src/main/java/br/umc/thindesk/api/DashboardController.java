package br.umc.thindesk.api;

import java.time.Clock;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.umc.thindesk.api.dto.IndicadoresDTO;
import br.umc.thindesk.api.dto.OpcoesFiltroDTO;
import br.umc.thindesk.api.dto.SerieTemporalDTO;
import br.umc.thindesk.api.dto.SituacaoDTO;
import br.umc.thindesk.api.dto.TecnicoRankingDTO;
import br.umc.thindesk.api.dto.TipoRankingDTO;
import br.umc.thindesk.dashboard.Agrupamento;
import br.umc.thindesk.dashboard.DashboardService;
import br.umc.thindesk.dashboard.FiltroChamados;

/**
 * Endpoints que alimentam o dashboard administrativo (somente ADMINISTRADOR,
 * regra aplicada no ApiSecurityConfig). Todos aceitam os filtros de FiltroRequest.
 */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

	private final DashboardService dashboardService;
	private final Clock clock;

	public DashboardController(DashboardService dashboardService, Clock clock) {
		this.dashboardService = dashboardService;
		this.clock = clock;
	}

	/** RF01 - indicadores gerais. */
	@GetMapping("/dashboard/indicadores")
	public IndicadoresDTO indicadores(@ModelAttribute FiltroRequest filtro) {
		return dashboardService.indicadores(FiltroChamados.paraDashboard(filtro, clock));
	}

	/** RF02 - abertos x resolvidos por dia, semana ou mes. */
	@GetMapping("/dashboard/atendimentos-por-periodo")
	public SerieTemporalDTO atendimentosPorPeriodo(@ModelAttribute FiltroRequest filtro,
			@RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
		return dashboardService.atendimentosPorPeriodo(FiltroChamados.paraDashboard(filtro, clock), agrupamento);
	}

	/** RF03 - situacao dos chamados (por status e prioridade). */
	@GetMapping("/dashboard/situacao")
	public SituacaoDTO situacao(@ModelAttribute FiltroRequest filtro) {
		return dashboardService.situacao(FiltroChamados.paraDashboard(filtro, clock));
	}

	/** RF04 - tipos de chamado mais atendidos. */
	@GetMapping("/dashboard/tipos-mais-atendidos")
	public List<TipoRankingDTO> tiposMaisAtendidos(@ModelAttribute FiltroRequest filtro,
			@RequestParam(defaultValue = "5") int limite) {
		return dashboardService.tiposMaisAtendidos(FiltroChamados.paraDashboard(filtro, clock), limite);
	}

	/** Ranking de tecnicos por chamados atendidos. */
	@GetMapping("/dashboard/ranking-tecnicos")
	public List<TecnicoRankingDTO> rankingTecnicos(@ModelAttribute FiltroRequest filtro,
			@RequestParam(defaultValue = "5") int limite) {
		return dashboardService.rankingTecnicos(FiltroChamados.paraDashboard(filtro, clock), limite);
	}

	/** RF05 - opcoes para os campos de filtro. */
	@GetMapping("/filtros/opcoes")
	public OpcoesFiltroDTO opcoes() {
		return dashboardService.opcoesDeFiltro();
	}

}
