package br.umc.thindesk.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoCollection;

import br.umc.thindesk.api.dto.IndicadoresDTO;
import br.umc.thindesk.api.dto.OpcoesFiltroDTO;
import br.umc.thindesk.api.dto.OpcoesFiltroDTO.Opcao;
import br.umc.thindesk.api.dto.SerieTemporalDTO;
import br.umc.thindesk.api.dto.SituacaoDTO;
import br.umc.thindesk.api.dto.TecnicoRankingDTO;
import br.umc.thindesk.api.dto.TipoRankingDTO;
import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.StatusChamado;
import br.umc.thindesk.chamado.TipoChamado;
import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.config.RelogioConfig;
import br.umc.thindesk.horario.HorarioAtendimento;
import br.umc.thindesk.horario.HorarioAtendimentoService;
import br.umc.thindesk.usuario.Perfil;
import br.umc.thindesk.usuario.UsuarioRepository;

/**
 * Calcula os dados do dashboard direto no MongoDB, com pipelines de agregacao
 * ($match + $group / $facet / $dateTrunc). Assim so os totais trafegam do banco
 * para a aplicacao, e nao a lista inteira de chamados.
 */
@Service
public class DashboardService {

	static final String COLECAO = "chamados";
	private static final String FUSO = RelogioConfig.FUSO.getId();
	private static final List<String> ATENDIDOS = List.of(StatusChamado.RESOLVIDO.name(), StatusChamado.FECHADO.name());
	private static final List<String> EM_ANDAMENTO = List.of(StatusChamado.EM_ATENDIMENTO.name(),
			StatusChamado.AGUARDANDO_SOLICITANTE.name());
	private static final double MS_POR_HORA = 3_600_000d;
	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private final MongoTemplate mongoTemplate;
	private final UsuarioRepository usuarioRepository;
	private final HorarioAtendimentoService horarioService;
	private final Clock clock;

	public DashboardService(MongoTemplate mongoTemplate, UsuarioRepository usuarioRepository,
			HorarioAtendimentoService horarioService, Clock clock) {
		this.mongoTemplate = mongoTemplate;
		this.usuarioRepository = usuarioRepository;
		this.horarioService = horarioService;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- RF01

	public IndicadoresDTO indicadores(FiltroChamados filtro) {
		Date agora = Date.from(clock.instant());
		Document grupo = new Document("_id", null)
				.append("total", new Document("$sum", 1))
				.append("abertos", contarSe(statusIgual(StatusChamado.ABERTO)))
				.append("emAtendimento", contarSe(statusIgual(StatusChamado.EM_ATENDIMENTO)))
				.append("aguardando", contarSe(statusIgual(StatusChamado.AGUARDANDO_SOLICITANTE)))
				.append("resolvidos", contarSe(statusIgual(StatusChamado.RESOLVIDO)))
				.append("fechados", contarSe(statusIgual(StatusChamado.FECHADO)))
				.append("cancelados", contarSe(statusIgual(StatusChamado.CANCELADO)))
				.append("atrasados", contarSe(expressaoAtrasado(agora)))
				.append("atendidos", contarSe(statusEm(ATENDIDOS)))
				.append("dentroSla", contarSe(expressaoDentroSla()))
				.append("mediaPrimeiroAtendimentoMs", new Document("$avg",
						new Document("$subtract", List.of("$dataInicioAtendimento", "$dataAbertura"))))
				.append("mediaResolucaoMs", new Document("$avg", duracaoResolucaoSeAtendido()));

		Document r = primeiro(agregar(List.of(new Document("$match", filtro.match("dataAbertura")),
				new Document("$group", grupo))));

		long total = longo(r, "total");
		long resolvidos = longo(r, "resolvidos");
		long fechados = longo(r, "fechados");
		long cancelados = longo(r, "cancelados");
		long atendidos = longo(r, "atendidos");
		long totalAnterior = colecao().countDocuments(filtro.periodoAnterior().match("dataAbertura"));

		return new IndicadoresDTO(filtro.inicio(), filtro.fim(), total, longo(r, "abertos"), longo(r, "emAtendimento"),
				longo(r, "aguardando"), resolvidos, fechados, cancelados, longo(r, "atrasados"),
				percentual(longo(r, "dentroSla"), atendidos),
				horas(r, "mediaPrimeiroAtendimentoMs"),
				horas(r, "mediaResolucaoMs"),
				percentual(resolvidos + fechados, total - cancelados),
				totalAnterior,
				totalAnterior == 0 ? null : arredondar((total - totalAnterior) * 100.0 / totalAnterior),
				contarForaDoExpediente(filtro));
	}

	/**
	 * Chamados do periodo abertos fora do horario de atendimento do setor.
	 * Le so dois campos (dataAbertura e setor) e compara com a grade de
	 * horarios carregada uma unica vez.
	 */
	long contarForaDoExpediente(FiltroChamados filtro) {
		Map<String, Map<java.time.DayOfWeek, HorarioAtendimento>> grade = horarioService.grade();
		if (grade.isEmpty()) {
			return 0;
		}
		long fora = 0;
		for (Document d : colecao().find(filtro.match("dataAbertura"))
				.projection(new Document("dataAbertura", 1).append("setor", 1))) {
			Date abertura = d.getDate("dataAbertura");
			if (abertura != null && !HorarioAtendimentoService.dentroDoExpediente(grade, d.getString("setor"),
					abertura.toInstant())) {
				fora++;
			}
		}
		return fora;
	}

	// ---------------------------------------------------------------- RF02

	public SerieTemporalDTO atendimentosPorPeriodo(FiltroChamados filtro, Agrupamento agrupamento) {
		Map<LocalDate, Long> abertos = contarPorPeriodo(filtro, "dataAbertura", agrupamento);
		Map<LocalDate, Long> resolvidos = contarPorPeriodo(filtro, "dataResolucao", agrupamento);

		List<SerieTemporalDTO.Ponto> pontos = new ArrayList<>();
		for (LocalDate periodo = inicioDoPeriodo(filtro.inicio(), agrupamento); !periodo.isAfter(filtro.fim());
				periodo = proximoPeriodo(periodo, agrupamento)) {
			pontos.add(new SerieTemporalDTO.Ponto(periodo, rotulo(periodo, agrupamento),
					abertos.getOrDefault(periodo, 0L), resolvidos.getOrDefault(periodo, 0L)));
		}
		return new SerieTemporalDTO(filtro.inicio(), filtro.fim(), agrupamento.name(), pontos);
	}

	private Map<LocalDate, Long> contarPorPeriodo(FiltroChamados filtro, String campoData, Agrupamento agrupamento) {
		Document dateTrunc = new Document("date", "$" + campoData)
				.append("unit", agrupamento.getUnidadeMongo())
				.append("timezone", FUSO);
		if (agrupamento == Agrupamento.SEMANA) {
			dateTrunc.append("startOfWeek", "monday");
		}
		List<Document> resultado = agregar(List.of(
				new Document("$match", filtro.match(campoData)),
				new Document("$group", new Document("_id", new Document("$dateTrunc", dateTrunc))
						.append("quantidade", new Document("$sum", 1)))));

		Map<LocalDate, Long> mapa = new HashMap<>();
		for (Document d : resultado) {
			Date periodo = d.getDate("_id");
			if (periodo != null) {
				mapa.put(periodo.toInstant().atZone(RelogioConfig.FUSO).toLocalDate(), longo(d, "quantidade"));
			}
		}
		return mapa;
	}

	static LocalDate inicioDoPeriodo(LocalDate data, Agrupamento agrupamento) {
		return switch (agrupamento) {
			case DIA -> data;
			case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MES -> data.withDayOfMonth(1);
		};
	}

	static LocalDate proximoPeriodo(LocalDate periodo, Agrupamento agrupamento) {
		return switch (agrupamento) {
			case DIA -> periodo.plusDays(1);
			case SEMANA -> periodo.plusWeeks(1);
			case MES -> periodo.plusMonths(1);
		};
	}

	static String rotulo(LocalDate periodo, Agrupamento agrupamento) {
		return switch (agrupamento) {
			case DIA -> periodo.format(DateTimeFormatter.ofPattern("dd/MM"));
			case SEMANA -> "sem. " + periodo.format(DateTimeFormatter.ofPattern("dd/MM"));
			case MES -> periodo.format(DateTimeFormatter.ofPattern("MMM/yy", PT_BR)).replace(".", "");
		};
	}

	// ---------------------------------------------------------------- RF03

	public SituacaoDTO situacao(FiltroChamados filtro) {
		Document facet = new Document()
				.append("porStatus", List.of(agruparContando("$status")))
				.append("porPrioridade", List.of(agruparContando("$prioridade")));
		Document r = primeiro(agregar(List.of(new Document("$match", filtro.match("dataAbertura")),
				new Document("$facet", facet))));

		Map<String, Long> porStatus = paraMapa(r.getList("porStatus", Document.class, List.of()));
		Map<String, Long> porPrioridade = paraMapa(r.getList("porPrioridade", Document.class, List.of()));
		long total = porStatus.values().stream().mapToLong(Long::longValue).sum();

		List<SituacaoDTO.Item> itensStatus = Arrays.stream(StatusChamado.values())
				.map(s -> item(s.name(), s.getDescricao(), porStatus, total)).toList();
		List<SituacaoDTO.Item> itensPrioridade = Arrays.stream(Prioridade.values())
				.map(p -> item(p.name(), p.getDescricao(), porPrioridade, total)).toList();
		return new SituacaoDTO(total, itensStatus, itensPrioridade);
	}

	private static SituacaoDTO.Item item(String codigo, String descricao, Map<String, Long> mapa, long total) {
		long quantidade = mapa.getOrDefault(codigo, 0L);
		Double pct = percentual(quantidade, total);
		return new SituacaoDTO.Item(codigo, descricao, quantidade, pct == null ? 0 : pct);
	}

	// ---------------------------------------------------------------- RF04

	public List<TipoRankingDTO> tiposMaisAtendidos(FiltroChamados filtro, int limite) {
		validarLimite(limite, TipoChamado.values().length);
		List<Document> grupos = agregar(List.of(
				new Document("$match", filtro.match("dataAbertura")),
				new Document("$group", new Document("_id", "$tipo")
						.append("total", new Document("$sum", 1))
						.append("atendidos", contarSe(statusEm(ATENDIDOS)))
						.append("mediaResolucaoMs", new Document("$avg", duracaoResolucaoSeAtendido())))));

		long totalAtendidos = grupos.stream().mapToLong(d -> longo(d, "atendidos")).sum();
		List<Document> ordenados = grupos.stream()
				.sorted(Comparator.comparingLong((Document d) -> longo(d, "atendidos")).reversed()
						.thenComparing(Comparator.comparingLong((Document d) -> longo(d, "total")).reversed()))
				.limit(limite)
				.toList();

		List<TipoRankingDTO> ranking = new ArrayList<>();
		for (int i = 0; i < ordenados.size(); i++) {
			Document d = ordenados.get(i);
			TipoChamado tipo = TipoChamado.valueOf(d.getString("_id"));
			Double pct = percentual(longo(d, "atendidos"), totalAtendidos);
			ranking.add(new TipoRankingDTO(i + 1, tipo.name(), tipo.getDescricao(), longo(d, "total"),
					longo(d, "atendidos"), pct == null ? 0 : pct, horas(d, "mediaResolucaoMs")));
		}
		return ranking;
	}

	// ---------------------------------------------------------- ranking tecnicos

	public List<TecnicoRankingDTO> rankingTecnicos(FiltroChamados filtro, int limite) {
		validarLimite(limite, 20);
		List<Document> grupos = agregar(List.of(
				new Document("$match", filtro.match("dataAbertura")),
				new Document("$match", new Document("tecnico.usuarioId", new Document("$ne", null))),
				new Document("$group", new Document("_id", new Document("id", "$tecnico.usuarioId")
						.append("nome", "$tecnico.nome"))
						.append("atendidos", contarSe(statusEm(ATENDIDOS)))
						.append("emAndamento", contarSe(statusEm(EM_ANDAMENTO)))
						.append("dentroSla", contarSe(expressaoDentroSla()))
						.append("mediaResolucaoMs", new Document("$avg", duracaoResolucaoSeAtendido())))));

		List<Document> ordenados = grupos.stream()
				.sorted(Comparator.comparingLong((Document d) -> longo(d, "atendidos")).reversed()
						.thenComparing(d -> ((Document) d.get("_id")).getString("nome"),
								Comparator.nullsLast(Comparator.naturalOrder())))
				.limit(limite)
				.toList();

		List<TecnicoRankingDTO> ranking = new ArrayList<>();
		for (int i = 0; i < ordenados.size(); i++) {
			Document d = ordenados.get(i);
			Document id = (Document) d.get("_id");
			long atendidos = longo(d, "atendidos");
			ranking.add(new TecnicoRankingDTO(i + 1, id.getString("id"), id.getString("nome"), atendidos,
					longo(d, "emAndamento"), horas(d, "mediaResolucaoMs"), percentual(longo(d, "dentroSla"), atendidos)));
		}
		return ranking;
	}

	// ---------------------------------------------------------------- RF05

	public OpcoesFiltroDTO opcoesDeFiltro() {
		List<Opcao> tipos = Arrays.stream(TipoChamado.values()).map(t -> new Opcao(t.name(), t.getDescricao())).toList();
		List<Opcao> status = Arrays.stream(StatusChamado.values()).map(s -> new Opcao(s.name(), s.getDescricao())).toList();
		List<Opcao> prioridades = Arrays.stream(Prioridade.values())
				.map(p -> new Opcao(p.name(), p.getDescricao() + " (SLA " + p.getSlaHoras() + "h)")).toList();
		List<Opcao> tecnicos = usuarioRepository.findByPerfil(Perfil.TECNICO).stream()
				.filter(u -> u.isAtivo())
				.map(u -> new Opcao(u.getId(), u.getNome()))
				.sorted(Comparator.comparing(Opcao::descricao))
				.toList();
		List<String> setores = mongoTemplate.findDistinct(new Query(), "setor", COLECAO, String.class).stream()
				.filter(s -> s != null && !s.isBlank())
				.sorted()
				.toList();
		List<Opcao> agrupamentos = List.of(new Opcao("DIA", "Dia"), new Opcao("SEMANA", "Semana"),
				new Opcao("MES", "Mês"));
		return new OpcoesFiltroDTO(tipos, status, prioridades, tecnicos, setores, agrupamentos);
	}

	// ------------------------------------------------------------- auxiliares

	private MongoCollection<Document> colecao() {
		return mongoTemplate.getCollection(COLECAO);
	}

	private List<Document> agregar(List<Document> pipeline) {
		return colecao().aggregate(pipeline).into(new ArrayList<>());
	}

	private static Document primeiro(List<Document> resultado) {
		return resultado.isEmpty() ? new Document() : resultado.get(0);
	}

	private static Document agruparContando(String campo) {
		return new Document("$group", new Document("_id", campo).append("quantidade", new Document("$sum", 1)));
	}

	private static Map<String, Long> paraMapa(List<Document> grupos) {
		Map<String, Long> mapa = new HashMap<>();
		for (Document d : grupos) {
			Object chave = d.get("_id");
			if (chave != null) {
				mapa.put(chave.toString(), longo(d, "quantidade"));
			}
		}
		return mapa;
	}

	private static Document contarSe(Object condicao) {
		return new Document("$sum", new Document("$cond", Arrays.asList(condicao, 1, 0)));
	}

	private static Document statusIgual(StatusChamado status) {
		return new Document("$eq", List.of("$status", status.name()));
	}

	private static Document statusEm(List<String> status) {
		return new Document("$in", List.of("$status", status));
	}

	/** Nao cancelado e (resolvido depois do prazo, ou ainda sem resolucao com o prazo vencido). */
	private static Document expressaoAtrasado(Date agora) {
		return new Document("$and", List.of(
				new Document("$ne", List.of("$status", StatusChamado.CANCELADO.name())),
				new Document("$gt", List.of(new Document("$ifNull", List.of("$dataResolucao", agora)), "$prazoSla"))));
	}

	/** Atendido (resolvido/fechado) com a resolucao dentro do prazo. */
	private static Document expressaoDentroSla() {
		return new Document("$and", List.of(statusEm(ATENDIDOS),
				new Document("$lte", List.of("$dataResolucao", "$prazoSla"))));
	}

	private static Document duracaoResolucaoSeAtendido() {
		return new Document("$cond", Arrays.asList(statusEm(ATENDIDOS),
				new Document("$subtract", List.of("$dataResolucao", "$dataAbertura")), null));
	}

	private static void validarLimite(int limite, int maximo) {
		if (limite < 1 || limite > maximo) {
			throw new ParametroInvalidoException("limite", "O parâmetro limite deve estar entre 1 e " + maximo + ".");
		}
	}

	static long longo(Document d, String chave) {
		Object valor = d.get(chave);
		return valor instanceof Number n ? n.longValue() : 0L;
	}

	private static Double horas(Document d, String chaveMs) {
		Object valor = d.get(chaveMs);
		return valor instanceof Number n ? arredondar(n.doubleValue() / MS_POR_HORA) : null;
	}

	static Double percentual(long parte, long todo) {
		return todo <= 0 ? null : arredondar(parte * 100.0 / todo);
	}

	static double arredondar(double valor) {
		return Math.round(valor * 10.0) / 10.0;
	}

}
