package br.umc.thindesk.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.bson.Document;

import br.umc.thindesk.api.FiltroRequest;
import br.umc.thindesk.chamado.Prioridade;
import br.umc.thindesk.chamado.StatusChamado;
import br.umc.thindesk.chamado.TipoChamado;
import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.config.RelogioConfig;

/**
 * Filtro ja validado e normalizado. As datas sao dias inteiros no fuso de
 * Sao Paulo: inicio as 00:00 e fim ate 23:59:59 (guardado como "fim
 * exclusivo" = 00:00 do dia seguinte). inicio/fim podem ser nulos na
 * listagem de chamados (sem filtro de periodo).
 */
public record FiltroChamados(
		LocalDate inicio,
		LocalDate fim,
		List<TipoChamado> tipos,
		List<StatusChamado> status,
		List<Prioridade> prioridades,
		String tecnicoId,
		String setor) {

	public static final int DIAS_PADRAO = 30;
	public static final int DIAS_MAXIMO = 366;

	public FiltroChamados {
		tipos = tipos == null ? List.of() : List.copyOf(tipos);
		status = status == null ? List.of() : List.copyOf(status);
		prioridades = prioridades == null ? List.of() : List.copyOf(prioridades);
		tecnicoId = vazioParaNulo(tecnicoId);
		setor = vazioParaNulo(setor);
	}

	/**
	 * Filtro do dashboard: periodo obrigatorio. Sem datas, usa os ultimos 30
	 * dias; so com uma das datas, completa a outra com 30 dias de janela.
	 */
	public static FiltroChamados paraDashboard(FiltroRequest req, Clock clock) {
		LocalDate hoje = LocalDate.now(clock.withZone(RelogioConfig.FUSO));
		LocalDate inicio = req.getInicio();
		LocalDate fim = req.getFim();
		if (fim == null) {
			fim = inicio == null ? hoje : minimo(inicio.plusDays(DIAS_PADRAO - 1), hoje);
		}
		if (inicio == null) {
			inicio = fim.minusDays(DIAS_PADRAO - 1);
		}
		validarPeriodo(inicio, fim);
		return new FiltroChamados(inicio, fim, req.getTipo(), req.getStatus(), req.getPrioridade(), req.getTecnicoId(),
				req.getSetor());
	}

	/** Filtro da listagem: periodo opcional (validado so se informado). */
	public static FiltroChamados paraListagem(FiltroRequest req) {
		if (req.getInicio() != null && req.getFim() != null) {
			validarPeriodo(req.getInicio(), req.getFim());
		}
		return new FiltroChamados(req.getInicio(), req.getFim(), req.getTipo(), req.getStatus(), req.getPrioridade(),
				req.getTecnicoId(), req.getSetor());
	}

	static void validarPeriodo(LocalDate inicio, LocalDate fim) {
		if (inicio.isAfter(fim)) {
			throw new ParametroInvalidoException("inicio",
					"A data de início (" + inicio + ") não pode ser posterior à data de fim (" + fim + ").");
		}
		if (ChronoUnit.DAYS.between(inicio, fim) + 1 > DIAS_MAXIMO) {
			throw new ParametroInvalidoException("fim", "O período máximo de consulta é de " + DIAS_MAXIMO + " dias.");
		}
	}

	public Instant inicioInstante() {
		return inicio == null ? null : inicio.atStartOfDay(RelogioConfig.FUSO).toInstant();
	}

	public Instant fimExclusivoInstante() {
		return fim == null ? null : fim.plusDays(1).atStartOfDay(RelogioConfig.FUSO).toInstant();
	}

	public long dias() {
		return ChronoUnit.DAYS.between(inicio, fim) + 1;
	}

	/** Mesmo filtro, deslocado para o periodo imediatamente anterior e de mesmo tamanho. */
	public FiltroChamados periodoAnterior() {
		long dias = dias();
		return new FiltroChamados(inicio.minusDays(dias), inicio.minusDays(1), tipos, status, prioridades, tecnicoId,
				setor);
	}

	public FiltroChamados semStatus() {
		return new FiltroChamados(inicio, fim, tipos, List.of(), prioridades, tecnicoId, setor);
	}

	/**
	 * Monta o estagio $match do MongoDB. O periodo e aplicado sobre o campo de
	 * data informado (dataAbertura na maioria dos indicadores; dataResolucao
	 * na serie de resolvidos).
	 */
	public Document match(String campoData) {
		Document match = new Document();
		if (inicio != null || fim != null) {
			Document periodo = new Document();
			if (inicio != null) {
				periodo.append("$gte", Date.from(inicioInstante()));
			}
			if (fim != null) {
				periodo.append("$lt", Date.from(fimExclusivoInstante()));
			}
			match.append(campoData, periodo);
		}
		if (!tipos.isEmpty()) {
			match.append("tipo", new Document("$in", nomes(tipos)));
		}
		if (!status.isEmpty()) {
			match.append("status", new Document("$in", nomes(status)));
		}
		if (!prioridades.isEmpty()) {
			match.append("prioridade", new Document("$in", nomes(prioridades)));
		}
		if (tecnicoId != null) {
			match.append("tecnico.usuarioId", tecnicoId);
		}
		if (setor != null) {
			match.append("setor", setor);
		}
		return match;
	}

	private static List<String> nomes(List<? extends Enum<?>> valores) {
		return valores.stream().map(Enum::name).toList();
	}

	private static String vazioParaNulo(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

	private static LocalDate minimo(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
