package br.umc.thindesk.chamado;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Regra de SLA")
class RegraSlaTest {

	private static final Instant ABERTURA = Instant.parse("2026-11-02T12:00:00Z");

	@Test
	void prazoSomaAsHorasDaPrioridade() {
		assertThat(RegraSla.calcularPrazo(Prioridade.CRITICA, ABERTURA)).isEqualTo(ABERTURA.plus(Duration.ofHours(8)));
		assertThat(RegraSla.calcularPrazo(Prioridade.BAIXA, ABERTURA)).isEqualTo(ABERTURA.plus(Duration.ofHours(72)));
	}

	@Test
	void abertoDentroDoPrazoNaoEstaAtrasado() {
		Chamado c = chamado(StatusChamado.ABERTO, Prioridade.ALTA, null);
		assertThat(RegraSla.estaAtrasado(c, ABERTURA.plus(Duration.ofHours(23)))).isFalse();
	}

	@Test
	void abertoComPrazoVencidoEstaAtrasado() {
		Chamado c = chamado(StatusChamado.EM_ATENDIMENTO, Prioridade.ALTA, null);
		assertThat(RegraSla.estaAtrasado(c, ABERTURA.plus(Duration.ofHours(25)))).isTrue();
	}

	@Test
	void resolvidoDepoisDoPrazoContinuaAtrasado() {
		Chamado c = chamado(StatusChamado.FECHADO, Prioridade.CRITICA, ABERTURA.plus(Duration.ofHours(9)));
		assertThat(RegraSla.estaAtrasado(c, ABERTURA.plus(Duration.ofDays(30)))).isTrue();
	}

	@Test
	void resolvidoNoPrazoNuncaFicaAtrasado() {
		Chamado c = chamado(StatusChamado.RESOLVIDO, Prioridade.CRITICA, ABERTURA.plus(Duration.ofHours(7)));
		assertThat(RegraSla.estaAtrasado(c, ABERTURA.plus(Duration.ofDays(30)))).isFalse();
	}

	@Test
	void canceladoNaoContaComoAtrasado() {
		Chamado c = chamado(StatusChamado.CANCELADO, Prioridade.CRITICA, null);
		assertThat(RegraSla.estaAtrasado(c, ABERTURA.plus(Duration.ofDays(30)))).isFalse();
	}

	private static Chamado chamado(StatusChamado status, Prioridade prioridade, Instant resolucao) {
		Chamado c = new Chamado();
		c.setStatus(status);
		c.setPrioridade(prioridade);
		c.setDataAbertura(ABERTURA);
		c.setPrazoSla(RegraSla.calcularPrazo(prioridade, ABERTURA));
		c.setDataResolucao(resolucao);
		return c;
	}

}
