package br.umc.thindesk.chamado;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import br.umc.thindesk.api.dto.ChamadoResumoDTO;
import br.umc.thindesk.api.dto.PaginaDTO;
import br.umc.thindesk.comum.ParametroInvalidoException;
import br.umc.thindesk.dashboard.FiltroChamados;

/**
 * Listagem paginada de chamados com filtros, busca textual e ordenacao.
 */
@Service
public class ChamadoConsultaService {

	public static final int TAMANHO_MAXIMO = 100;
	static final Set<String> CAMPOS_ORDENACAO = Set.of("dataAbertura", "protocolo", "prazoSla", "dataResolucao",
			"status", "prioridade", "tipo");

	private final MongoTemplate mongoTemplate;
	private final Clock clock;

	public ChamadoConsultaService(MongoTemplate mongoTemplate, Clock clock) {
		this.mongoTemplate = mongoTemplate;
		this.clock = clock;
	}

	/**
	 * @param solicitanteId quando informado, restringe aos chamados desse solicitante
	 */
	public PaginaDTO<ChamadoResumoDTO> listar(FiltroChamados filtro, String busca, int pagina, int tamanho,
			String ordenacao, String solicitanteId) {
		if (pagina < 0) {
			throw new ParametroInvalidoException("page", "O parâmetro page deve ser maior ou igual a 0.");
		}
		if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
			throw new ParametroInvalidoException("size", "O parâmetro size deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
		}

		Query query = new Query(criterios(filtro, busca, solicitanteId));
		long total = mongoTemplate.count(query, Chamado.class);
		query.with(ordenar(ordenacao)).skip((long) pagina * tamanho).limit(tamanho);

		Instant agora = clock.instant();
		List<ChamadoResumoDTO> conteudo = mongoTemplate.find(query, Chamado.class).stream()
				.map(c -> ChamadoResumoDTO.de(c, agora))
				.toList();
		int totalPaginas = (int) Math.ceil(total / (double) tamanho);
		return new PaginaDTO<>(conteudo, pagina, tamanho, total, totalPaginas);
	}

	static Criteria criterios(FiltroChamados filtro, String busca, String solicitanteId) {
		List<Criteria> partes = new ArrayList<>();
		if (filtro.inicio() != null || filtro.fim() != null) {
			Criteria periodo = Criteria.where("dataAbertura");
			if (filtro.inicio() != null) {
				periodo = periodo.gte(filtro.inicioInstante());
			}
			if (filtro.fim() != null) {
				periodo = periodo.lt(filtro.fimExclusivoInstante());
			}
			partes.add(periodo);
		}
		if (!filtro.tipos().isEmpty()) {
			partes.add(Criteria.where("tipo").in(filtro.tipos()));
		}
		if (!filtro.status().isEmpty()) {
			partes.add(Criteria.where("status").in(filtro.status()));
		}
		if (!filtro.prioridades().isEmpty()) {
			partes.add(Criteria.where("prioridade").in(filtro.prioridades()));
		}
		if (filtro.tecnicoId() != null) {
			partes.add(Criteria.where("tecnico.usuarioId").is(filtro.tecnicoId()));
		}
		if (filtro.setor() != null) {
			partes.add(Criteria.where("setor").is(filtro.setor()));
		}
		if (solicitanteId != null) {
			partes.add(Criteria.where("solicitante.usuarioId").is(solicitanteId));
		}
		if (busca != null && !busca.isBlank()) {
			Pattern padrao = Pattern.compile(Pattern.quote(busca.trim()), Pattern.CASE_INSENSITIVE);
			partes.add(new Criteria().orOperator(Criteria.where("protocolo").regex(padrao),
					Criteria.where("titulo").regex(padrao)));
		}
		return partes.isEmpty() ? new Criteria() : new Criteria().andOperator(partes);
	}

	/** Formato "campo,asc|desc" (padrao dataAbertura,desc), so com campos permitidos. */
	static Sort ordenar(String ordenacao) {
		if (ordenacao == null || ordenacao.isBlank()) {
			return Sort.by(Sort.Direction.DESC, "dataAbertura");
		}
		String[] partes = ordenacao.split(",");
		String campo = partes[0].trim();
		if (!CAMPOS_ORDENACAO.contains(campo)) {
			throw new ParametroInvalidoException("sort", "Ordenação por '" + campo + "' não é permitida. Use um de: "
					+ String.join(", ", CAMPOS_ORDENACAO.stream().sorted().toList()) + ".");
		}
		Sort.Direction direcao = Sort.Direction.DESC;
		if (partes.length > 1) {
			String dir = partes[1].trim().toLowerCase();
			if (!dir.equals("asc") && !dir.equals("desc")) {
				throw new ParametroInvalidoException("sort", "A direção da ordenação deve ser asc ou desc.");
			}
			direcao = dir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
		}
		return Sort.by(direcao, campo).and(Sort.by(Sort.Direction.DESC, "_id"));
	}

}
