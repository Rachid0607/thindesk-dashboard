package br.umc.thindesk.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.umc.thindesk.auditoria.AuditoriaService;
import br.umc.thindesk.auditoria.TipoEvento;
import br.umc.thindesk.comum.RecursoNaoEncontradoException;
import br.umc.thindesk.comum.RegraNegocioException;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.Usuario;
import br.umc.thindesk.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ciclo de vida do chamado")
class ChamadoServiceTest {

	private static final Instant AGORA = Instant.parse("2026-11-14T15:00:00Z");
	private static final Pessoa SOLICITANTE = new Pessoa("sol-1", "Ana");
	private static final Pessoa TECNICO = new Pessoa("tec-1", "Carlos");
	private static final Pessoa ADMIN = new Pessoa("adm-1", "Admin");

	@Mock
	private ChamadoRepository chamadoRepository;
	@Mock
	private UsuarioRepository usuarioRepository;
	@Mock
	private GeradorProtocolo geradorProtocolo;
	@Mock
	private AuditoriaService auditoriaService;

	private ChamadoService service;

	@BeforeEach
	void setUp() {
		service = new ChamadoService(chamadoRepository, usuarioRepository, geradorProtocolo, auditoriaService,
				Clock.fixed(AGORA, ZoneOffset.UTC));
	}

	@Test
	void abrirPreencheStatusPrazoProtocoloEHistorico() {
		when(geradorProtocolo.proximo(AGORA)).thenReturn("TD-2026-000001");
		when(chamadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Chamado c = service.abrir("  Sem internet  ", "Cabo solto", TipoChamado.REDE, Prioridade.CRITICA, "Financeiro",
				SOLICITANTE);

		assertThat(c.getTitulo()).isEqualTo("Sem internet");
		assertThat(c.getStatus()).isEqualTo(StatusChamado.ABERTO);
		assertThat(c.getProtocolo()).isEqualTo("TD-2026-000001");
		assertThat(c.getPrazoSla()).isEqualTo(AGORA.plus(Duration.ofHours(8)));
		assertThat(c.getHistorico()).singleElement().satisfies(h -> {
			assertThat(h.de()).isNull();
			assertThat(h.para()).isEqualTo(StatusChamado.ABERTO);
		});
		verify(auditoriaService).registrar(eq(TipoEvento.CHAMADO_ABERTO), eq("sol-1"), any());
	}

	@Test
	void tecnicoAoIniciarAtendimentoViraResponsavel() {
		Chamado c = existente(StatusChamado.ABERTO);
		when(chamadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Chamado r = service.alterarStatus("c1", StatusChamado.EM_ATENDIMENTO, null, "Verificando", TECNICO, Perfil.TECNICO);

		assertThat(r.getTecnico()).isEqualTo(TECNICO);
		assertThat(r.getDataInicioAtendimento()).isEqualTo(AGORA);
		assertThat(r.getConsideracoes()).singleElement().extracting(ConsideracaoTecnica::texto).isEqualTo("Verificando");
		assertThat(r.getHistorico()).last().extracting(MudancaStatus::para).isEqualTo(StatusChamado.EM_ATENDIMENTO);
		assertThat(c).isSameAs(r);
	}

	@Test
	void administradorPrecisaInformarTecnicoParaIniciarAtendimento() {
		existente(StatusChamado.ABERTO);
		assertThatThrownBy(() -> service.alterarStatus("c1", StatusChamado.EM_ATENDIMENTO, null, null, ADMIN,
				Perfil.ADMINISTRADOR)).isInstanceOf(RegraNegocioException.class);
		verify(chamadoRepository, never()).save(any());
	}

	@Test
	void administradorAtribuiTecnicoValido() {
		existente(StatusChamado.ABERTO);
		Usuario tecnico = new Usuario();
		tecnico.setId("tec-1");
		tecnico.setNome("Carlos");
		tecnico.setPerfil(Perfil.TECNICO);
		tecnico.setAtivo(true);
		when(usuarioRepository.findById("tec-1")).thenReturn(Optional.of(tecnico));
		when(chamadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Chamado r = service.alterarStatus("c1", StatusChamado.EM_ATENDIMENTO, "tec-1", null, ADMIN, Perfil.ADMINISTRADOR);

		assertThat(r.getTecnico()).isEqualTo(TECNICO);
	}

	@Test
	void tecnicoInformadoPrecisaTerPerfilTecnico() {
		existente(StatusChamado.ABERTO);
		Usuario solicitante = new Usuario();
		solicitante.setId("sol-1");
		solicitante.setPerfil(Perfil.SOLICITANTE);
		solicitante.setAtivo(true);
		when(usuarioRepository.findById("sol-1")).thenReturn(Optional.of(solicitante));

		assertThatThrownBy(() -> service.alterarStatus("c1", StatusChamado.EM_ATENDIMENTO, "sol-1", null, ADMIN,
				Perfil.ADMINISTRADOR)).isInstanceOf(RegraNegocioException.class).hasMessageContaining("sol-1");
	}

	@Test
	void transicaoInvalidaGeraExcecaoComOsDestinosPermitidos() {
		existente(StatusChamado.ABERTO);
		assertThatThrownBy(() -> service.alterarStatus("c1", StatusChamado.FECHADO, null, null, TECNICO, Perfil.TECNICO))
				.isInstanceOf(TransicaoStatusInvalidaException.class)
				.hasMessageContaining("ABERTO")
				.hasMessageContaining("FECHADO");
	}

	@Test
	void resolverRegistraDataDeResolucaoEReabrirLimpa() {
		Chamado c = existente(StatusChamado.EM_ATENDIMENTO);
		c.setTecnico(TECNICO);
		when(chamadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.alterarStatus("c1", StatusChamado.RESOLVIDO, null, null, TECNICO, Perfil.TECNICO);
		assertThat(c.getDataResolucao()).isEqualTo(AGORA);

		service.alterarStatus("c1", StatusChamado.EM_ATENDIMENTO, null, "Voltou a falhar", TECNICO, Perfil.TECNICO);
		assertThat(c.getDataResolucao()).isNull();
		assertThat(c.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
	}

	@Test
	void fecharRegistraDataDeFechamento() {
		Chamado c = existente(StatusChamado.RESOLVIDO);
		when(chamadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		service.alterarStatus("c1", StatusChamado.FECHADO, null, null, SOLICITANTE, Perfil.SOLICITANTE);
		assertThat(c.getDataFechamento()).isEqualTo(AGORA);
	}

	@Test
	void consideracaoEmChamadoFinalizadoEhRecusada() {
		existente(StatusChamado.FECHADO);
		assertThatThrownBy(() -> service.adicionarConsideracao("c1", "texto", TECNICO))
				.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void consideracaoEhRegistradaComAutorEData() {
		Chamado c = existente(StatusChamado.EM_ATENDIMENTO);
		when(chamadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		service.adicionarConsideracao("c1", "  Trocado o cabo  ", TECNICO);
		assertThat(c.getConsideracoes()).singleElement()
				.isEqualTo(new ConsideracaoTecnica("Trocado o cabo", TECNICO, AGORA));
	}

	@Test
	void chamadoInexistenteGera404() {
		when(chamadoRepository.findById("x")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.buscar("x")).isInstanceOf(RecursoNaoEncontradoException.class);
	}

	private Chamado existente(StatusChamado status) {
		Chamado c = new Chamado();
		c.setId("c1");
		c.setProtocolo("TD-2026-000010");
		c.setStatus(status);
		c.setPrioridade(Prioridade.MEDIA);
		c.setDataAbertura(AGORA.minus(Duration.ofHours(5)));
		c.setPrazoSla(RegraSla.calcularPrazo(Prioridade.MEDIA, c.getDataAbertura()));
		c.setSolicitante(SOLICITANTE);
		when(chamadoRepository.findById("c1")).thenReturn(Optional.of(c));
		return c;
	}

}
