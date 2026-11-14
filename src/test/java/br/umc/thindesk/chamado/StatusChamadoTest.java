package br.umc.thindesk.chamado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Máquina de estados do chamado")
class StatusChamadoTest {

	@ParameterizedTest(name = "{0} -> {1} é permitido")
	@CsvSource({
		"ABERTO, EM_ATENDIMENTO",
		"ABERTO, CANCELADO",
		"EM_ATENDIMENTO, AGUARDANDO_SOLICITANTE",
		"EM_ATENDIMENTO, RESOLVIDO",
		"EM_ATENDIMENTO, CANCELADO",
		"AGUARDANDO_SOLICITANTE, EM_ATENDIMENTO",
		"RESOLVIDO, FECHADO",
		"RESOLVIDO, EM_ATENDIMENTO"
	})
	void transicoesPermitidas(StatusChamado de, StatusChamado para) {
		assertThat(de.podeIrPara(para)).isTrue();
	}

	@ParameterizedTest(name = "{0} -> {1} é bloqueado")
	@CsvSource({
		"ABERTO, RESOLVIDO",
		"ABERTO, FECHADO",
		"EM_ATENDIMENTO, ABERTO",
		"RESOLVIDO, ABERTO",
		"FECHADO, EM_ATENDIMENTO",
		"CANCELADO, ABERTO",
		"ABERTO, ABERTO"
	})
	void transicoesBloqueadas(StatusChamado de, StatusChamado para) {
		assertThat(de.podeIrPara(para)).isFalse();
	}

	@Test
	void fechadoECanceladoSaoFinais() {
		assertThat(StatusChamado.FECHADO.isFinal()).isTrue();
		assertThat(StatusChamado.CANCELADO.isFinal()).isTrue();
		assertThat(StatusChamado.RESOLVIDO.isFinal()).isFalse();
	}

	@Test
	void destinoNuloNuncaEPermitido() {
		assertThat(StatusChamado.ABERTO.podeIrPara(null)).isFalse();
	}

	@Test
	void atendidosSaoResolvidoEFechado() {
		assertThat(StatusChamado.RESOLVIDO.isAtendido()).isTrue();
		assertThat(StatusChamado.FECHADO.isAtendido()).isTrue();
		assertThat(StatusChamado.EM_ATENDIMENTO.isAtendido()).isFalse();
	}

}
