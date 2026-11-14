package br.umc.thindesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.umc.thindesk.api.dto.IndicadoresDTO;
import br.umc.thindesk.api.dto.PaginaDTO;
import br.umc.thindesk.api.dto.SituacaoDTO;
import br.umc.thindesk.chamado.Chamado;
import br.umc.thindesk.chamado.ChamadoConsultaService;
import br.umc.thindesk.chamado.ChamadoService;
import br.umc.thindesk.chamado.Pessoa;
import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.RegraSla;
import br.umc.thindesk.chamado.StatusChamado;
import br.umc.thindesk.chamado.TipoChamado;
import br.umc.thindesk.chamado.TransicaoStatusInvalidaException;
import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.comum.RecursoNaoEncontradoException;
import br.umc.thindesk.config.RelogioConfig;
import br.umc.thindesk.dashboard.DashboardService;
import br.umc.thindesk.dashboard.FiltroChamados;
import br.umc.thindesk.seguranca.ApiSecurityConfig;
import br.umc.thindesk.seguranca.UsuarioAutenticado;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.Usuario;

/**
 * Testes da camada web da API: roteamento, seguranca por perfil, validacao de
 * parametros/corpo e formato dos erros. Os services sao simulados (Mockito).
 */
@WebMvcTest(controllers = { DashboardController.class, ChamadoController.class })
@Import({ ApiSecurityConfig.class, RelogioConfig.class })
@DisplayName("API REST /api/v1")
class ApiControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DashboardService dashboardService;
	@MockitoBean
	private ChamadoService chamadoService;
	@MockitoBean
	private ChamadoConsultaService consultaService;
	@MockitoBean
	private AuthenticationManager authenticationManager;

	private static UsuarioAutenticado logado(String id, Perfil perfil) {
		Usuario u = new Usuario();
		u.setId(id);
		u.setNome("Usuário " + id);
		u.setEmail(id + "@thindesk.local");
		u.setSenhaHash("x");
		u.setPerfil(perfil);
		u.setAtivo(true);
		return new UsuarioAutenticado(u);
	}

	private static final UsuarioAutenticado ADMIN = logado("adm", Perfil.ADMINISTRADOR);
	private static final UsuarioAutenticado TECNICO = logado("tec", Perfil.TECNICO);
	private static final UsuarioAutenticado SOLICITANTE = logado("sol", Perfil.SOLICITANTE);

	@Nested
	@DisplayName("Segurança")
	class Seguranca {

		@Test
		void semLoginResponde401EmJsonSemPedirSenhaAoNavegador() throws Exception {
			mvc.perform(get("/api/v1/dashboard/indicadores"))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(header().doesNotExist("WWW-Authenticate"))
					.andExpect(jsonPath("$.status").value(401))
					.andExpect(jsonPath("$.type").value("/erros/nao-autenticado"));
		}

		@Test
		void tecnicoNaoAcessaODashboard() throws Exception {
			mvc.perform(get("/api/v1/dashboard/indicadores").with(user(TECNICO)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.title").value("Acesso negado"));
		}

		@Test
		void solicitanteNaoMudaStatus() throws Exception {
			mvc.perform(patch("/api/v1/chamados/c1/status").with(user(SOLICITANTE)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ATENDIMENTO\"}"))
					.andExpect(status().isForbidden());
			verify(chamadoService, never()).alterarStatus(any(), any(), any(), any(), any(), any());
		}

		@Test
		void escritaComSessaoExigeTokenCsrf() throws Exception {
			mvc.perform(post("/api/v1/chamados").with(user(ADMIN))
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isForbidden());
		}

		@Test
		void solicitanteNaoVeChamadoDeOutraPessoa() throws Exception {
			when(chamadoService.buscar("c1")).thenReturn(chamado("c1", new Pessoa("outra", "Outra pessoa")));
			mvc.perform(get("/api/v1/chamados/c1").with(user(SOLICITANTE)))
					.andExpect(status().isForbidden());
		}

		@Test
		void solicitanteListaSoOsProprios() throws Exception {
			when(consultaService.listar(any(), any(), anyInt(), anyInt(), any(), any()))
					.thenReturn(new PaginaDTO<>(List.of(), 0, 10, 0, 0));
			mvc.perform(get("/api/v1/chamados").with(user(SOLICITANTE))).andExpect(status().isOk());
			verify(consultaService).listar(any(), isNull(), eq(0), eq(10), isNull(), eq("sol"));
		}

		@Test
		void rotaInexistenteDaApiEhNegada() throws Exception {
			mvc.perform(get("/api/v1/nao-existe").with(user(ADMIN))).andExpect(status().isForbidden());
		}

	}

	@Nested
	@DisplayName("Dashboard")
	class Dashboard {

		@Test
		void indicadoresRespondemJson() throws Exception {
			when(dashboardService.indicadores(any())).thenReturn(new IndicadoresDTO(LocalDate.of(2026, 10, 16),
					LocalDate.of(2026, 11, 14), 10, 2, 1, 1, 3, 2, 1, 2, 80.0, 1.5, 20.0, 55.6, 8, 25.0));
			mvc.perform(get("/api/v1/dashboard/indicadores").with(user(ADMIN)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.total").value(10))
					.andExpect(jsonPath("$.percentualDentroSla").value(80.0))
					.andExpect(jsonPath("$.inicio").value("2026-10-16"));
		}

		@Test
		void periodoInvertidoResponde400ComOCampo() throws Exception {
			mvc.perform(get("/api/v1/dashboard/indicadores").with(user(ADMIN))
					.param("inicio", "2026-11-10").param("fim", "2026-11-01"))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.type").value("/erros/parametro-invalido"))
					.andExpect(jsonPath("$.erros[0].campo").value("inicio"));
		}

		@Test
		void dataEmFormatoErradoResponde400() throws Exception {
			mvc.perform(get("/api/v1/dashboard/situacao").with(user(ADMIN)).param("inicio", "14/11/2026"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.erros[0].campo").value("inicio"));
		}

		@Test
		void statusDesconhecidoResponde400() throws Exception {
			mvc.perform(get("/api/v1/dashboard/situacao").with(user(ADMIN)).param("status", "PERDIDO"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.erros[*].campo", hasItem("status")));
		}

		@Test
		void agrupamentoInvalidoResponde400() throws Exception {
			mvc.perform(get("/api/v1/dashboard/atendimentos-por-periodo").with(user(ADMIN)).param("agrupamento", "ANO"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.detail", containsString("agrupamento")));
		}

		@Test
		void filtrosMultiplosSaoRepassadosAoServico() throws Exception {
			when(dashboardService.situacao(any())).thenAnswer(inv -> {
				FiltroChamados f = inv.getArgument(0);
				assertThat(f.status()).containsExactly(StatusChamado.ABERTO, StatusChamado.RESOLVIDO);
				assertThat(f.tipos()).containsExactly(TipoChamado.REDE);
				return new SituacaoDTO(0, List.of(), List.of());
			});
			mvc.perform(get("/api/v1/dashboard/situacao").with(user(ADMIN))
					.param("status", "ABERTO,RESOLVIDO").param("tipo", "REDE"))
					.andExpect(status().isOk());
		}

	}

	@Nested
	@DisplayName("Chamados")
	class Chamados {

		@Test
		void criarChamadoResponde201ComLocation() throws Exception {
			when(chamadoService.abrir(any(), any(), any(), any(), any(), any()))
					.thenReturn(chamado("novo", new Pessoa("adm", "Admin")));
			mvc.perform(post("/api/v1/chamados").with(user(ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"titulo\":\"Sem internet\",\"descricao\":\"Cabo\",\"tipo\":\"REDE\",\"prioridade\":\"ALTA\",\"setor\":\"TI\"}"))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", containsString("/api/v1/chamados/novo")))
					.andExpect(jsonPath("$.protocolo").value("TD-2026-000001"))
					.andExpect(jsonPath("$.proximosStatusPermitidos", hasItem("EM_ATENDIMENTO")));
		}

		@Test
		void corpoInvalidoResponde422ComOsCampos() throws Exception {
			mvc.perform(post("/api/v1/chamados").with(user(ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Oi\",\"prioridade\":\"ALTA\"}"))
					.andExpect(status().is(422))
					.andExpect(jsonPath("$.type").value("/erros/validacao"))
					.andExpect(jsonPath("$.erros[*].campo", hasItem("titulo")))
					.andExpect(jsonPath("$.erros[*].campo", hasItem("tipo")))
					.andExpect(jsonPath("$.erros[*].campo", hasItem("setor")));
		}

		@Test
		void jsonMalformadoResponde400() throws Exception {
			mvc.perform(post("/api/v1/chamados").with(user(ADMIN)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"titulo\": "))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.type").value("/erros/corpo-invalido"));
		}

		@Test
		void chamadoInexistenteResponde404() throws Exception {
			when(chamadoService.buscar("x")).thenThrow(new RecursoNaoEncontradoException("Chamado x não encontrado."));
			mvc.perform(get("/api/v1/chamados/x").with(user(ADMIN)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("Chamado x não encontrado."))
					.andExpect(jsonPath("$.instance").value("/api/v1/chamados/x"));
		}

		@Test
		void transicaoInvalidaResponde409ComOsDestinosPermitidos() throws Exception {
			when(chamadoService.alterarStatus(eq("c1"), eq(StatusChamado.FECHADO), any(), any(), any(), any()))
					.thenThrow(new TransicaoStatusInvalidaException(StatusChamado.ABERTO, StatusChamado.FECHADO));
			mvc.perform(patch("/api/v1/chamados/c1/status").with(user(TECNICO)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FECHADO\"}"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.statusAtual").value("ABERTO"))
					.andExpect(jsonPath("$.destinosPermitidos", hasItem("EM_ATENDIMENTO")));
		}

		@Test
		void tecnicoMudaStatusComSucesso() throws Exception {
			Chamado c = chamado("c1", new Pessoa("sol", "Sol"));
			c.setStatus(StatusChamado.EM_ATENDIMENTO);
			when(chamadoService.alterarStatus(eq("c1"), eq(StatusChamado.EM_ATENDIMENTO), isNull(), eq("Analisando"),
					any(), eq(Perfil.TECNICO))).thenReturn(c);
			mvc.perform(patch("/api/v1/chamados/c1/status").with(user(TECNICO)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"EM_ATENDIMENTO\",\"comentario\":\"Analisando\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));
		}

		@Test
		void ordenacaoInvalidaResponde400() throws Exception {
			when(consultaService.listar(any(), any(), anyInt(), anyInt(), eq("senha,asc"), any()))
					.thenThrow(new ParametroInvalidoException("sort", "Ordenação não permitida."));
			mvc.perform(get("/api/v1/chamados").with(user(ADMIN)).param("sort", "senha,asc"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.erros[0].campo").value("sort"));
		}

	}

	private static Chamado chamado(String id, Pessoa solicitante) {
		Instant abertura = Instant.parse("2026-11-14T12:00:00Z");
		Chamado c = new Chamado();
		c.setId(id);
		c.setProtocolo("TD-2026-000001");
		c.setTitulo("Sem internet");
		c.setDescricao("Cabo");
		c.setTipo(TipoChamado.REDE);
		c.setPrioridade(Prioridade.ALTA);
		c.setStatus(StatusChamado.ABERTO);
		c.setSetor("TI");
		c.setSolicitante(solicitante);
		c.setDataAbertura(abertura);
		c.setPrazoSla(RegraSla.calcularPrazo(Prioridade.ALTA, abertura));
		return c;
	}

}
