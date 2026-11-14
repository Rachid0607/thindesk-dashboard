package br.umc.thindesk.chamado;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.umc.thindesk.cliente.Cliente;
import br.umc.thindesk.cliente.ClienteRepository;
import br.umc.thindesk.config.RelogioConfig;
import br.umc.thindesk.horario.HorarioAtendimento;
import br.umc.thindesk.horario.HorarioAtendimentoRepository;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.Usuario;
import br.umc.thindesk.usuario.UsuarioRepository;
import br.umc.thindesk.usuario.UsuarioService;

/**
 * Popula o banco com tecnicos, clientes, horarios de atendimento e chamados
 * de demonstracao (12 meses de historico), para o dashboard ter o que mostrar. So roda com
 * thindesk.seed.enabled=true (variavel THINDESK_SEED) e so quando a colecao de
 * chamados esta vazia, entao pode ficar ligada sem duplicar dados.
 * A semente do Random e fixa: a mesma data de execucao gera os mesmos dados.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "thindesk.seed.enabled", havingValue = "true")
public class CargaInicialChamados implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CargaInicialChamados.class);

	static final int QUANTIDADE = 420;

	private static final String[][] TECNICOS = {
		{ "Carlos Andrade", "carlos.andrade" },
		{ "Fernanda Lima", "fernanda.lima" },
		{ "Rafael Souza", "rafael.souza" },
		{ "Juliana Martins", "juliana.martins" },
		{ "Bruno Oliveira", "bruno.oliveira" }
	};

	private static final String[] SETORES = { "Financeiro", "Recursos Humanos", "Comercial", "Operações", "Jurídico",
			"Atendimento" };

	private static final String[] SOLICITANTES = { "Ana Paula Ribeiro", "Marcos Vinícius", "Patrícia Gomes",
			"Lucas Ferreira", "Camila Rocha", "Diego Santos", "Beatriz Almeida", "Gustavo Pereira", "Larissa Costa",
			"Thiago Barbosa", "Mariana Lopes", "Eduardo Nunes", "Renata Carvalho", "Felipe Araújo", "Aline Teixeira",
			"Rodrigo Mendes", "Vanessa Duarte", "Henrique Moraes", "Priscila Cardoso", "Leandro Pinto" };

	private static final Map<TipoChamado, String[]> TITULOS = Map.of(
			TipoChamado.HARDWARE, new String[] { "Computador não liga", "Monitor piscando", "Teclado com teclas falhando",
					"Notebook superaquecendo", "Mouse sem resposta", "Troca de memória RAM" },
			TipoChamado.SOFTWARE, new String[] { "Excel travando ao abrir planilha", "Instalação do pacote Office",
					"Atualização do navegador", "Erro ao abrir PDF", "Licença do software expirada",
					"Programa fechando sozinho" },
			TipoChamado.REDE, new String[] { "Sem acesso à internet", "Wi-Fi caindo toda hora", "VPN não conecta",
					"Lentidão na rede", "Ponto de rede sem sinal" },
			TipoChamado.ACESSO_SENHA, new String[] { "Senha bloqueada", "Reset de senha do e-mail",
					"Liberar acesso à pasta compartilhada", "Novo usuário no domínio", "Acesso negado ao sistema" },
			TipoChamado.EMAIL, new String[] { "Caixa de e-mail cheia", "E-mails não chegam", "Configurar assinatura",
					"Outlook pedindo senha toda hora", "E-mail indo para spam" },
			TipoChamado.IMPRESSORA, new String[] { "Impressora não imprime", "Papel atolando", "Instalar impressora de rede",
					"Impressão saindo borrada", "Scanner não envia para e-mail" },
			TipoChamado.SISTEMA_INTERNO, new String[] { "Erro ao gerar relatório no ERP", "Tela do sistema em branco",
					"Lançamento não aparece no sistema", "Erro 500 no portal interno", "Integração com banco falhou" },
			TipoChamado.OUTROS, new String[] { "Dúvida sobre uso do sistema", "Solicitação de equipamento novo",
					"Mudança de mesa (reinstalar estação)", "Orientação sobre backup" });

	private static final Map<TipoChamado, String[]> CONSIDERACOES = Map.of(
			TipoChamado.HARDWARE, new String[] { "Fonte com defeito, substituída por uma do estoque.",
					"Cabo de vídeo com mau contato, trocado.", "Limpeza interna e troca de pasta térmica." },
			TipoChamado.SOFTWARE, new String[] { "Reinstalado o programa e limpo o cache do perfil.",
					"Aplicada a atualização mais recente do fornecedor.", "Licença renovada no portal do fabricante." },
			TipoChamado.REDE, new String[] { "Switch do andar reiniciado; conexão normalizada.",
					"Perfil da VPN recriado na máquina do usuário.", "Cabo de rede trocado no ponto da mesa." },
			TipoChamado.ACESSO_SENHA, new String[] { "Senha redefinida e usuário orientado sobre a política de senhas.",
					"Permissão concedida após aprovação do gestor.", "Conta desbloqueada no AD." },
			TipoChamado.EMAIL, new String[] { "Arquivamento automático configurado.",
					"Regra de encaminhamento incorreta removida.", "Perfil do Outlook recriado." },
			TipoChamado.IMPRESSORA, new String[] { "Driver reinstalado e fila de impressão limpa.",
					"Rolete de tração substituído.", "Impressora adicionada via servidor de impressão." },
			TipoChamado.SISTEMA_INTERNO, new String[] { "Erro reproduzido e encaminhado ao fornecedor do ERP.",
					"Cache do servidor de aplicação limpo.", "Parâmetro de integração corrigido em produção." },
			TipoChamado.OUTROS, new String[] { "Usuário orientado por chamada de vídeo.", "Pedido encaminhado ao setor de compras." });

	private final ChamadoRepository chamadoRepository;
	private final ClienteRepository clienteRepository;
	private final HorarioAtendimentoRepository horarioRepository;
	private final UsuarioRepository usuarioRepository;
	private final UsuarioService usuarioService;
	private final PasswordEncoder passwordEncoder;
	private final GeradorProtocolo geradorProtocolo;
	private final Environment environment;
	private final Clock clock;

	public CargaInicialChamados(ChamadoRepository chamadoRepository, ClienteRepository clienteRepository,
			HorarioAtendimentoRepository horarioRepository, UsuarioRepository usuarioRepository,
			UsuarioService usuarioService, PasswordEncoder passwordEncoder, GeradorProtocolo geradorProtocolo,
			Environment environment, Clock clock) {
		this.chamadoRepository = chamadoRepository;
		this.clienteRepository = clienteRepository;
		this.horarioRepository = horarioRepository;
		this.usuarioRepository = usuarioRepository;
		this.usuarioService = usuarioService;
		this.passwordEncoder = passwordEncoder;
		this.geradorProtocolo = geradorProtocolo;
		this.environment = environment;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<Pessoa> tecnicos = garantirTecnicos();
		garantirHorarios();
		if (chamadoRepository.count() > 0) {
			log.info("Carga inicial ignorada: a colecao de chamados ja possui dados.");
			return;
		}
		List<Cliente> clientes = garantirClientes();
		List<Chamado> chamados = gerar(tecnicos, clientes, clock.instant(), new Random(2026));
		chamadoRepository.saveAll(chamados);
		log.info("Carga inicial concluida: {} chamados de demonstracao criados.", chamados.size());
	}

	private List<Pessoa> garantirTecnicos() {
		String senhaDemo = environment.getProperty("DEMO_SENHA");
		List<Pessoa> tecnicos = new ArrayList<>();
		for (String[] dados : TECNICOS) {
			String email = dados[1] + "@thindesk.local";
			Usuario usuario = usuarioRepository.findByEmail(email).orElseGet(() -> {
				Usuario novo = new Usuario();
				novo.setNome(dados[0]);
				novo.setEmail(email);
				// Sem DEMO_SENHA, a senha e aleatoria: o tecnico existe para os dados, mas ninguem loga com ele.
				String senha = senhaDemo == null || senhaDemo.isBlank() ? UUID.randomUUID().toString() : senhaDemo;
				novo.setSenhaHash(passwordEncoder.encode(senha));
				novo.setPerfil(Perfil.TECNICO);
				novo.setAtivo(true);
				novo.setConsentimentoTermosEm(clock.instant());
				return usuarioService.salvar(novo);
			});
			tecnicos.add(new Pessoa(usuario.getId(), usuario.getNome()));
		}
		return tecnicos;
	}

	/** Clientes de demonstracao (os solicitantes dos chamados), cada um em um setor. */
	private List<Cliente> garantirClientes() {
		if (clienteRepository.count() > 0) {
			return clienteRepository.findAll();
		}
		List<Cliente> clientes = new ArrayList<>();
		for (int i = 0; i < SOLICITANTES.length; i++) {
			Cliente c = new Cliente();
			c.setNome(SOLICITANTES[i]);
			c.setSetor(SETORES[i % SETORES.length]);
			c.setTelefone(String.format("(11) 9%04d-%04d", 1000 + i * 37, 2000 + i * 53));
			c.setCriadoEm(clock.instant());
			clientes.add(c);
		}
		return clienteRepository.saveAll(clientes);
	}

	/** Expediente padrao: segunda a sexta, 08:00-18:00; o Atendimento tambem abre sabado de manha. */
	private void garantirHorarios() {
		if (horarioRepository.count() > 0) {
			return;
		}
		List<HorarioAtendimento> horarios = new ArrayList<>();
		for (String setor : SETORES) {
			for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
					DayOfWeek.FRIDAY)) {
				horarios.add(new HorarioAtendimento(setor, dia, "08:00", "18:00"));
			}
		}
		horarios.add(new HorarioAtendimento("Atendimento", DayOfWeek.SATURDAY, "08:00", "12:00"));
		horarioRepository.saveAll(horarios);
	}

	/**
	 * Gera os chamados em memoria (sem acesso ao banco, exceto o ajuste final
	 * dos contadores de protocolo).
	 */
	List<Chamado> gerar(List<Pessoa> tecnicos, List<Cliente> clientes, Instant agora, Random random) {
		List<Chamado> chamados = new ArrayList<>();
		for (int i = 0; i < QUANTIDADE; i++) {
			chamados.add(gerarUm(tecnicos, clientes.get(random.nextInt(clientes.size())), agora, random));
		}
		chamados.sort(Comparator.comparing(Chamado::getDataAbertura));

		Map<Integer, Long> sequencias = new HashMap<>();
		for (Chamado chamado : chamados) {
			int ano = chamado.getDataAbertura().atZone(RelogioConfig.FUSO).getYear();
			long seq = sequencias.merge(ano, 1L, Long::sum);
			chamado.setProtocolo(GeradorProtocolo.formatar(ano, seq));
		}
		sequencias.forEach(geradorProtocolo::ajustarMinimo);
		return chamados;
	}

	private Chamado gerarUm(List<Pessoa> tecnicos, Cliente cliente, Instant agora, Random random) {
		Instant abertura = sortearAbertura(agora, random);
		TipoChamado tipo = sortearTipo(random);
		Prioridade prioridade = sortearPrioridade(random);
		String nomeSolicitante = cliente.getNome();
		Pessoa solicitante = new Pessoa(cliente.getId(), nomeSolicitante);
		Pessoa tecnico = tecnicos.get(random.nextInt(tecnicos.size()));

		Chamado chamado = new Chamado();
		chamado.setTipo(tipo);
		chamado.setPrioridade(prioridade);
		chamado.setSetor(cliente.getSetor());
		chamado.setSolicitante(solicitante);
		String[] titulos = TITULOS.get(tipo);
		chamado.setTitulo(titulos[random.nextInt(titulos.length)]);
		chamado.setDescricao("Chamado aberto por " + nomeSolicitante + ": " + chamado.getTitulo().toLowerCase() + ".");
		chamado.setDataAbertura(abertura);
		chamado.setPrazoSla(RegraSla.calcularPrazo(prioridade, abertura));
		chamado.setStatus(StatusChamado.ABERTO);
		chamado.getHistorico().add(new MudancaStatus(null, StatusChamado.ABERTO, solicitante, abertura));

		// 6% cancelados ainda na fila
		if (random.nextDouble() < 0.06) {
			Instant cancelamento = minimo(abertura.plus(horas(random, 1, 30)), agora);
			mudar(chamado, StatusChamado.CANCELADO, solicitante, cancelamento);
			chamado.setDataFechamento(cancelamento);
			return chamado;
		}

		// primeiro atendimento: mais rapido quanto maior a prioridade
		double fatorPrioridade = prioridade.getSlaHoras() / 48.0;
		Instant inicio = abertura.plus(horas(random, 0.1, 6 * fatorPrioridade + 0.5));
		if (inicio.isAfter(agora)) {
			return chamado; // ainda na fila
		}
		chamado.setTecnico(tecnico);
		chamado.setDataInicioAtendimento(inicio);
		mudar(chamado, StatusChamado.EM_ATENDIMENTO, tecnico, inicio);

		// tempo de resolucao como fracao do SLA; ~13% estouram o prazo
		double fracao = random.nextDouble() < 0.13 ? 1.05 + random.nextDouble() * 0.9 : 0.12 + random.nextDouble() * 0.8;
		Instant resolucao = abertura.plusSeconds((long) (prioridade.getSla().toSeconds() * fracao));
		if (!resolucao.isAfter(inicio)) {
			resolucao = inicio.plus(Duration.ofMinutes(20 + random.nextInt(60)));
		}

		if (random.nextDouble() < 0.12) {
			Instant aguardando = inicio.plus(Duration.between(inicio, resolucao).dividedBy(3));
			if (aguardando.isAfter(agora)) {
				return chamado;
			}
			mudar(chamado, StatusChamado.AGUARDANDO_SOLICITANTE, tecnico, aguardando);
			Instant retorno = inicio.plus(Duration.between(inicio, resolucao).dividedBy(2));
			if (retorno.isAfter(agora)) {
				return chamado;
			}
			mudar(chamado, StatusChamado.EM_ATENDIMENTO, solicitante, retorno);
		}

		if (resolucao.isAfter(agora)) {
			return chamado; // segue em atendimento
		}
		String[] textos = CONSIDERACOES.get(tipo);
		chamado.getConsideracoes().add(new ConsideracaoTecnica(textos[random.nextInt(textos.length)], tecnico, resolucao));
		chamado.setDataResolucao(resolucao);
		mudar(chamado, StatusChamado.RESOLVIDO, tecnico, resolucao);

		Instant fechamento = resolucao.plus(horas(random, 2, 72));
		if (fechamento.isAfter(agora)) {
			return chamado; // resolvido, aguardando confirmacao
		}
		chamado.setDataFechamento(fechamento);
		mudar(chamado, StatusChamado.FECHADO, solicitante, fechamento);
		return chamado;
	}

	private static void mudar(Chamado chamado, StatusChamado destino, Pessoa usuario, Instant data) {
		chamado.getHistorico().add(new MudancaStatus(chamado.getStatus(), destino, usuario, data));
		chamado.setStatus(destino);
	}

	/**
	 * Abertura nos ultimos 365 dias, quase sempre em dia util e horario
	 * comercial (uma parte cai a noite ou no fim de semana, fora do
	 * expediente), com mais volume nos meses recentes.
	 */
	private static Instant sortearAbertura(Instant agora, Random random) {
		LocalDate hoje = agora.atZone(RelogioConfig.FUSO).toLocalDate();
		while (true) {
			int diasAtras = (int) Math.floor(Math.pow(random.nextDouble(), 1.25) * 365);
			LocalDate dia = hoje.minusDays(diasAtras);
			if ((dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY)
					&& random.nextDouble() < 0.9) {
				continue;
			}
			int hora = random.nextDouble() < 0.07 ? 18 + random.nextInt(4) : 8 + random.nextInt(10);
			ZonedDateTime momento = dia.atTime(hora, random.nextInt(60)).atZone(RelogioConfig.FUSO);
			Instant abertura = momento.toInstant();
			if (!abertura.isAfter(agora)) {
				return abertura;
			}
		}
	}

	private static TipoChamado sortearTipo(Random random) {
		return sortear(random, new TipoChamado[] { TipoChamado.SOFTWARE, TipoChamado.ACESSO_SENHA, TipoChamado.HARDWARE,
				TipoChamado.REDE, TipoChamado.EMAIL, TipoChamado.IMPRESSORA, TipoChamado.SISTEMA_INTERNO,
				TipoChamado.OUTROS }, new int[] { 22, 20, 14, 12, 10, 9, 9, 4 });
	}

	private static Prioridade sortearPrioridade(Random random) {
		return sortear(random, Prioridade.values(), new int[] { 30, 40, 22, 8 });
	}

	private static <T> T sortear(Random random, T[] valores, int[] pesos) {
		int total = 0;
		for (int peso : pesos) {
			total += peso;
		}
		int sorteio = random.nextInt(total);
		for (int i = 0; i < valores.length; i++) {
			sorteio -= pesos[i];
			if (sorteio < 0) {
				return valores[i];
			}
		}
		return valores[valores.length - 1];
	}

	private static Duration horas(Random random, double minimo, double maximo) {
		double horas = minimo + random.nextDouble() * (maximo - minimo);
		return Duration.ofSeconds((long) (horas * 3600));
	}

	private static Instant minimo(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

}
