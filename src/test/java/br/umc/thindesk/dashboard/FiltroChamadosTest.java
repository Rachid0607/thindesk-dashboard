package br.umc.thindesk.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.umc.thindesk.api.FiltroRequest;
import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.StatusChamado;
import br.umc.thindesk.chamado.TipoChamado;
import br.umc.thindesk.comum.ParametroInvalidoException;

@DisplayName("Filtro do dashboard")
class FiltroChamadosTest {

	// 14/11/2026 15:00 em Sao Paulo
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

	@Test
	void semDatasUsaOsUltimos30Dias() {
		FiltroChamados f = FiltroChamados.paraDashboard(new FiltroRequest(), CLOCK);
		assertThat(f.fim()).isEqualTo(LocalDate.of(2026, 11, 14));
		assertThat(f.inicio()).isEqualTo(LocalDate.of(2026, 10, 16));
		assertThat(f.dias()).isEqualTo(30);
	}

	@Test
	void soComInicioCompletaJanelaDe30DiasSemPassarDeHoje() {
		FiltroRequest req = new FiltroRequest();
		req.setInicio(LocalDate.of(2026, 11, 1));
		FiltroChamados f = FiltroChamados.paraDashboard(req, CLOCK);
		assertThat(f.fim()).isEqualTo(LocalDate.of(2026, 11, 14));
	}

	@Test
	void inicioDepoisDoFimEhRecusado() {
		FiltroRequest req = new FiltroRequest();
		req.setInicio(LocalDate.of(2026, 11, 10));
		req.setFim(LocalDate.of(2026, 11, 1));
		assertThatThrownBy(() -> FiltroChamados.paraDashboard(req, CLOCK))
				.isInstanceOf(ParametroInvalidoException.class)
				.hasMessageContaining("não pode ser posterior");
	}

	@Test
	void periodoMaiorQue366DiasEhRecusado() {
		FiltroRequest req = new FiltroRequest();
		req.setInicio(LocalDate.of(2025, 1, 1));
		req.setFim(LocalDate.of(2026, 11, 14));
		assertThatThrownBy(() -> FiltroChamados.paraDashboard(req, CLOCK))
				.isInstanceOf(ParametroInvalidoException.class)
				.extracting("parametro").isEqualTo("fim");
	}

	@Test
	void datasSaoInterpretadasNoFusoDeSaoPaulo() {
		FiltroChamados f = new FiltroChamados(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), null, null, null,
				null, null);
		assertThat(f.inicioInstante()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
		assertThat(f.fimExclusivoInstante()).isEqualTo(Instant.parse("2026-11-02T03:00:00Z"));
	}

	@Test
	void periodoAnteriorTemOMesmoTamanho() {
		FiltroChamados f = new FiltroChamados(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null, null, null,
				null, null);
		FiltroChamados anterior = f.periodoAnterior();
		assertThat(anterior.inicio()).isEqualTo(LocalDate.of(2026, 10, 22));
		assertThat(anterior.fim()).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	@Test
	void matchMontaTodosOsCriterios() {
		FiltroChamados f = new FiltroChamados(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
				List.of(TipoChamado.REDE), List.of(StatusChamado.ABERTO, StatusChamado.RESOLVIDO),
				List.of(Prioridade.ALTA), " tec-1 ", "Financeiro");
		Document match = f.match("dataAbertura");

		Document periodo = (Document) match.get("dataAbertura");
		assertThat(periodo.get("$gte")).isEqualTo(Date.from(Instant.parse("2026-11-01T03:00:00Z")));
		assertThat(periodo.get("$lt")).isEqualTo(Date.from(Instant.parse("2026-11-03T03:00:00Z")));
		assertThat(((Document) match.get("tipo")).get("$in")).isEqualTo(List.of("REDE"));
		assertThat(((Document) match.get("status")).get("$in")).isEqualTo(List.of("ABERTO", "RESOLVIDO"));
		assertThat(((Document) match.get("prioridade")).get("$in")).isEqualTo(List.of("ALTA"));
		assertThat(match.get("tecnico.usuarioId")).isEqualTo("tec-1");
		assertThat(match.get("setor")).isEqualTo("Financeiro");
	}

	@Test
	void textosVaziosSaoIgnorados() {
		FiltroChamados f = new FiltroChamados(null, null, null, null, null, "  ", "");
		assertThat(f.match("dataAbertura")).isEmpty();
	}

}
