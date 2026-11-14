package br.umc.thindesk.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.umc.thindesk.usuario.UsuarioRepository;
import br.umc.thindesk.usuario.UsuarioService;

@DisplayName("Carga de dados de demonstração")
class CargaInicialChamadosTest {

	private static final Instant AGORA = Instant.parse("2026-11-14T18:00:00Z");

	private final CargaInicialChamados carga = new CargaInicialChamados(mock(ChamadoRepository.class),
			mock(UsuarioRepository.class), mock(UsuarioService.class), mock(PasswordEncoder.class),
			mock(GeradorProtocolo.class), mock(Environment.class), Clock.systemUTC());

	private final List<Pessoa> tecnicos = List.of(new Pessoa("t1", "Carlos"), new Pessoa("t2", "Fernanda"));

	@Test
	void geraChamadosCoerentesComAMaquinaDeEstados() {
		List<Chamado> chamados = carga.gerar(tecnicos, AGORA, new Random(2026));

		assertThat(chamados).hasSize(CargaInicialChamados.QUANTIDADE);
		Set<String> protocolos = new HashSet<>();
		for (Chamado c : chamados) {
			assertThat(protocolos.add(c.getProtocolo())).as("protocolo unico").isTrue();
			assertThat(c.getDataAbertura()).isBeforeOrEqualTo(AGORA);
			assertThat(c.getPrazoSla()).isEqualTo(RegraSla.calcularPrazo(c.getPrioridade(), c.getDataAbertura()));

			// o historico comeca em ABERTO, so tem transicoes validas e termina no status atual
			List<MudancaStatus> historico = c.getHistorico();
			assertThat(historico.get(0).para()).isEqualTo(StatusChamado.ABERTO);
			for (int i = 1; i < historico.size(); i++) {
				MudancaStatus m = historico.get(i);
				assertThat(m.de().podeIrPara(m.para())).as("%s -> %s", m.de(), m.para()).isTrue();
				assertThat(m.data()).isAfterOrEqualTo(historico.get(i - 1).data()).isBeforeOrEqualTo(AGORA);
			}
			assertThat(historico.get(historico.size() - 1).para()).isEqualTo(c.getStatus());

			if (c.getStatus().isAtendido()) {
				assertThat(c.getDataResolucao()).isNotNull().isAfter(c.getDataAbertura());
				assertThat(c.getTecnico()).isNotNull();
			}
			if (c.getStatus() == StatusChamado.ABERTO) {
				assertThat(c.getTecnico()).isNull();
			}
		}
	}

	@Test
	void distribuicaoTemAtrasadosEVariosStatus() {
		List<Chamado> chamados = carga.gerar(tecnicos, AGORA, new Random(2026));
		long atrasados = chamados.stream().filter(c -> RegraSla.estaAtrasado(c, AGORA)).count();
		assertThat(atrasados).isBetween(20L, 120L);
		assertThat(chamados).extracting(Chamado::getStatus).contains(StatusChamado.FECHADO, StatusChamado.CANCELADO,
				StatusChamado.RESOLVIDO);
	}

	@Test
	void mesmaSementeGeraOsMesmosDados() {
		List<Chamado> a = carga.gerar(tecnicos, AGORA, new Random(7));
		List<Chamado> b = carga.gerar(tecnicos, AGORA, new Random(7));
		assertThat(a).extracting(Chamado::getProtocolo, Chamado::getStatus, Chamado::getDataAbertura)
				.isEqualTo(b.stream().map(c -> org.assertj.core.groups.Tuple.tuple(c.getProtocolo(), c.getStatus(),
						c.getDataAbertura())).toList());
	}

}
