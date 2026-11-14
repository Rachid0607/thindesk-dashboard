package br.umc.thindesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.umc.thindesk.chamado.Chamado;
import br.umc.thindesk.chamado.ChamadoRepository;
import br.umc.thindesk.chamado.ConsideracaoTecnica;
import br.umc.thindesk.chamado.MudancaStatus;
import br.umc.thindesk.chamado.Pessoa;
import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.RegraSla;
import br.umc.thindesk.chamado.StatusChamado;
import br.umc.thindesk.chamado.TipoChamado;
import br.umc.thindesk.config.RelogioConfig;
import br.umc.thindesk.horario.HorarioAtendimento;
import br.umc.thindesk.horario.HorarioAtendimentoRepository;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.Usuario;
import br.umc.thindesk.usuario.UsuarioRepository;

/**
 * Teste de integracao ponta a ponta: aplicacao completa + MongoDB real
 * (definido em MONGODB_URI). Grava uma massa de
 * dados conhecida e confere os numeros calculados pelas agregacoes, a
 * autenticacao HTTP Basic e o ciclo de vida de um chamado pela API.
 */
@SpringBootTest(properties = "thindesk.seed.enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MONGODB_URI", matches = ".+")
@DisplayName("Integração API + MongoDB")
class ApiIntegracaoTest {

	private static final String SENHA = "SenhaTeste123";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private MongoTemplate mongoTemplate;
	@Autowired
	private ChamadoRepository chamadoRepository;
	@Autowired
	private UsuarioRepository usuarioRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private HorarioAtendimentoRepository horarioRepository;

	private Usuario admin;
	private Usuario tecnico1;
	private Usuario tecnico2;

	@BeforeEach
	void prepararBanco() {
		mongoTemplate.dropCollection("chamados");
		mongoTemplate.dropCollection("usuarios");
		mongoTemplate.dropCollection("contadores");
		mongoTemplate.dropCollection("tentativas_login");
		mongoTemplate.dropCollection("clientes");
		mongoTemplate.dropCollection("horariosAtendimento");

		admin = usuario("admin@teste.local", "Administrador", Perfil.ADMINISTRADOR);
		tecnico1 = usuario("tec1@teste.local", "Técnico Um", Perfil.TECNICO);
		tecnico2 = usuario("tec2@teste.local", "Técnico Dois", Perfil.TECNICO);

		Instant agora = Instant.now();
		Pessoa t1 = new Pessoa(tecnico1.getId(), tecnico1.getNome());
		Pessoa t2 = new Pessoa(tecnico2.getId(), tecnico2.getNome());

		// 1) aberto ha 20h, critico (SLA 8h) -> atrasado
		salvar("TD-T-1", StatusChamado.ABERTO, Prioridade.CRITICA, TipoChamado.REDE, agora.minus(Duration.ofHours(20)),
				null, null, null, null);
		// 2) em atendimento ha 1 dia, baixa (SLA 72h), 1h ate o inicio -> no prazo
		Instant a2 = agora.minus(Duration.ofDays(1));
		salvar("TD-T-2", StatusChamado.EM_ATENDIMENTO, Prioridade.BAIXA, TipoChamado.SOFTWARE, a2, t2,
				a2.plus(Duration.ofHours(1)), null, null);
		// 3) resolvido em 10h, alta (SLA 24h), 2h ate o inicio -> no prazo
		Instant a3 = agora.minus(Duration.ofDays(5));
		salvar("TD-T-3", StatusChamado.RESOLVIDO, Prioridade.ALTA, TipoChamado.SOFTWARE, a3, t1,
				a3.plus(Duration.ofHours(2)), a3.plus(Duration.ofHours(10)), null);
		// 4) fechado, resolvido em 60h, media (SLA 48h), 4h ate o inicio -> atrasado
		Instant a4 = agora.minus(Duration.ofDays(10));
		salvar("TD-T-4", StatusChamado.FECHADO, Prioridade.MEDIA, TipoChamado.HARDWARE, a4, t1,
				a4.plus(Duration.ofHours(4)), a4.plus(Duration.ofHours(60)), a4.plus(Duration.ofHours(70)));
		// 5) cancelado
		salvar("TD-T-5", StatusChamado.CANCELADO, Prioridade.BAIXA, TipoChamado.OUTROS, agora.minus(Duration.ofDays(3)),
				null, null, null, agora.minus(Duration.ofDays(2)));
		// 6) fora da janela padrao de 30 dias (entra no periodo anterior)
		Instant a6 = agora.minus(Duration.ofDays(40));
		salvar("TD-T-6", StatusChamado.FECHADO, Prioridade.BAIXA, TipoChamado.SOFTWARE, a6, t2,
				a6.plus(Duration.ofHours(1)), a6.plus(Duration.ofHours(5)), a6.plus(Duration.ofHours(6)));
	}

	@Test
	void indicadoresCalculadosPeloBanco() throws Exception {
		mvc.perform(get("/api/v1/dashboard/indicadores").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.abertos").value(1))
				.andExpect(jsonPath("$.emAtendimento").value(1))
				.andExpect(jsonPath("$.resolvidos").value(1))
				.andExpect(jsonPath("$.fechados").value(1))
				.andExpect(jsonPath("$.cancelados").value(1))
				.andExpect(jsonPath("$.atrasados").value(2))
				.andExpect(jsonPath("$.percentualDentroSla").value(50.0))
				.andExpect(jsonPath("$.tempoMedioPrimeiroAtendimentoHoras").value(2.3))
				.andExpect(jsonPath("$.tempoMedioResolucaoHoras").value(35.0))
				.andExpect(jsonPath("$.taxaResolucaoPercentual").value(50.0))
				.andExpect(jsonPath("$.totalPeriodoAnterior").value(1))
				.andExpect(jsonPath("$.variacaoTotalPercentual").value(400.0));
	}

	@Test
	void situacaoTiposERanking() throws Exception {
		mvc.perform(get("/api/v1/dashboard/situacao").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.porStatus.length()").value(6))
				.andExpect(jsonPath("$.porStatus[?(@.codigo=='ABERTO')].quantidade").value(1))
				.andExpect(jsonPath("$.porStatus[?(@.codigo=='ABERTO')].percentual").value(20.0));

		mvc.perform(get("/api/v1/dashboard/tipos-mais-atendidos").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].codigo").value("SOFTWARE"))
				.andExpect(jsonPath("$[0].atendidos").value(1))
				.andExpect(jsonPath("$[0].total").value(2))
				.andExpect(jsonPath("$[0].percentual").value(50.0))
				.andExpect(jsonPath("$[1].codigo").value("HARDWARE"));

		mvc.perform(get("/api/v1/dashboard/ranking-tecnicos").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nome").value("Técnico Um"))
				.andExpect(jsonPath("$[0].atendidos").value(2))
				.andExpect(jsonPath("$[0].percentualDentroSla").value(50.0))
				.andExpect(jsonPath("$[1].nome").value("Técnico Dois"))
				.andExpect(jsonPath("$[1].emAndamento").value(1));
	}

	@Test
	void serieTemporalPreencheTodosOsDias() throws Exception {
		MvcResult r = mvc.perform(get("/api/v1/dashboard/atendimentos-por-periodo").param("agrupamento", "DIA")
				.with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pontos.length()").value(30))
				.andReturn();
		List<Integer> abertos = JsonPath.read(r.getResponse().getContentAsString(), "$.pontos[*].abertos");
		List<Integer> resolvidos = JsonPath.read(r.getResponse().getContentAsString(), "$.pontos[*].resolvidos");
		assertThat(abertos.stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);
		assertThat(resolvidos.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
	}

	@Test
	void filtroPorTipoEPrioridade() throws Exception {
		mvc.perform(get("/api/v1/dashboard/indicadores").param("tipo", "SOFTWARE,HARDWARE")
				.param("prioridade", "ALTA,MEDIA").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.atrasados").value(1));
	}

	@Test
	void opcoesDeFiltroVemDoBanco() throws Exception {
		mvc.perform(get("/api/v1/filtros/opcoes").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tecnicos.length()").value(2))
				.andExpect(jsonPath("$.setores[0]").value("Financeiro"))
				.andExpect(jsonPath("$.tipos.length()").value(8));
	}

	@Test
	void listagemPaginadaComBusca() throws Exception {
		mvc.perform(get("/api/v1/chamados").param("size", "2").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(6))
				.andExpect(jsonPath("$.totalPaginas").value(3))
				.andExpect(jsonPath("$.conteudo[0].protocolo").value("TD-T-1"));

		mvc.perform(get("/api/v1/chamados").param("busca", "td-t-4").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.conteudo[0].atrasado").value(true));
	}

	@Test
	void cicloDeVidaDeUmChamadoPelaApi() throws Exception {
		MvcResult criado = mvc.perform(post("/api/v1/chamados").with(httpBasic(admin.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Monitor piscando\",\"descricao\":\"Tela pisca ao ligar\",\"tipo\":\"HARDWARE\",\"prioridade\":\"MEDIA\",\"setor\":\"Comercial\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ABERTO"))
				.andExpect(jsonPath("$.protocolo", matchesPattern("TD-\\d{4}-\\d{6}")))
				.andReturn();
		String id = JsonPath.read(criado.getResponse().getContentAsString(), "$.id");

		mvc.perform(patch("/api/v1/chamados/" + id + "/status").with(httpBasic(admin.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"EM_ATENDIMENTO\",\"tecnicoId\":\"" + tecnico1.getId() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tecnico.nome").value("Técnico Um"));

		mvc.perform(patch("/api/v1/chamados/" + id + "/status").with(httpBasic(tecnico1.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ABERTO\"}"))
				.andExpect(status().isConflict());

		mvc.perform(post("/api/v1/chamados/" + id + "/consideracoes").with(httpBasic(tecnico1.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Cabo de vídeo trocado\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.consideracoes[0].texto").value("Cabo de vídeo trocado"));

		mvc.perform(patch("/api/v1/chamados/" + id + "/status").with(httpBasic(tecnico1.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVIDO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dataResolucao").exists())
				.andExpect(jsonPath("$.historico.length()").value(3))
				.andExpect(jsonPath("$.atrasado").value(false));
	}

	@Test
	void chamadosAbertosForaDoExpedienteDoSetor() throws Exception {
		for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			horarioRepository.save(new HorarioAtendimento("Financeiro", dia, "08:00", "18:00"));
		}
		mongoTemplate.dropCollection("chamados");
		LocalDate hoje = LocalDate.now(RelogioConfig.FUSO);
		Instant domingo = hoje.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
				.atTime(10, 0).atZone(RelogioConfig.FUSO).toInstant();
		Instant quarta = hoje.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY))
				.atTime(10, 0).atZone(RelogioConfig.FUSO).toInstant();
		salvar("TD-T-7", StatusChamado.ABERTO, Prioridade.BAIXA, TipoChamado.REDE, domingo, null, null, null, null);
		salvar("TD-T-8", StatusChamado.ABERTO, Prioridade.BAIXA, TipoChamado.REDE, quarta, null, null, null, null);

		mvc.perform(get("/api/v1/dashboard/indicadores").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.abertosForaDoExpediente").value(1));

		mvc.perform(get("/api/v1/horarios-atendimento").param("setor", "Financeiro")
				.with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[0].diaSemana").value("MONDAY"));
	}

	@Test
	void clientesPelaApiEChamadoEmNomeDoCliente() throws Exception {
		MvcResult criado = mvc.perform(post("/api/v1/clientes").with(httpBasic(tecnico1.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Marina Prado\",\"telefone\":\"(11) 91234-5678\",\"setor\":\"Jurídico\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.setor").value("Jurídico"))
				.andReturn();
		String clienteId = JsonPath.read(criado.getResponse().getContentAsString(), "$.id");

		mvc.perform(post("/api/v1/clientes").with(httpBasic(tecnico1.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X\",\"telefone\":\"abc\"}"))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.erros[*].campo", hasItem("telefone")));

		mvc.perform(get("/api/v1/clientes").param("setor", "Jurídico").with(httpBasic(admin.getEmail(), SENHA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nome").value("Marina Prado"));

		mvc.perform(post("/api/v1/chamados").with(httpBasic(tecnico1.getEmail(), SENHA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Acesso ao sistema de processos\",\"descricao\":\"Sem acesso\",\"tipo\":\"ACESSO_SENHA\",\"prioridade\":\"ALTA\",\"clienteId\":\"" + clienteId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.setor").value("Jurídico"))
				.andExpect(jsonPath("$.solicitante.nome").value("Marina Prado"));

		mvc.perform(get("/api/v1/horarios-atendimento").with(httpBasic(tecnico1.getEmail(), SENHA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void credenciaisErradasEPerfilSemPermissao() throws Exception {
		mvc.perform(get("/api/v1/dashboard/indicadores").with(httpBasic(admin.getEmail(), "errada")))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/dashboard/indicadores").with(httpBasic(tecnico1.getEmail(), SENHA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void paginaDoDashboardSemSessaoVaiParaOLogin() throws Exception {
		mvc.perform(get("/admin/dashboard"))
				.andExpect(status().is3xxRedirection());
	}

	private Usuario usuario(String email, String nome, Perfil perfil) {
		Usuario u = new Usuario();
		u.setEmail(email);
		u.setNome(nome);
		u.setPerfil(perfil);
		u.setAtivo(true);
		u.setSenhaHash(passwordEncoder.encode(SENHA));
		u.setCriadoEm(Instant.now());
		u.setConsentimentoTermosEm(Instant.now());
		return usuarioRepository.save(u);
	}

	private void salvar(String protocolo, StatusChamado status, Prioridade prioridade, TipoChamado tipo,
			Instant abertura, Pessoa tecnico, Instant inicio, Instant resolucao, Instant fechamento) {
		Chamado c = new Chamado();
		c.setProtocolo(protocolo);
		c.setTitulo("Chamado " + protocolo);
		c.setDescricao("Teste");
		c.setTipo(tipo);
		c.setPrioridade(prioridade);
		c.setStatus(status);
		c.setSetor("Financeiro");
		c.setSolicitante(new Pessoa("sol-1", "Solicitante"));
		c.setTecnico(tecnico);
		c.setDataAbertura(abertura);
		c.setDataInicioAtendimento(inicio);
		c.setDataResolucao(resolucao);
		c.setDataFechamento(fechamento);
		c.setPrazoSla(RegraSla.calcularPrazo(prioridade, abertura));
		c.getHistorico().add(new MudancaStatus(null, StatusChamado.ABERTO, c.getSolicitante(), abertura));
		if (resolucao != null) {
			c.getConsideracoes().add(new ConsideracaoTecnica("Resolvido", tecnico, resolucao));
		}
		chamadoRepository.save(c);
	}

}
