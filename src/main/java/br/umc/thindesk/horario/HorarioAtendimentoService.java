package br.umc.thindesk.horario;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.umc.thindesk.comum.RegraNegocioException;
import br.umc.thindesk.config.RelogioConfig;

/**
 * Horarios de atendimento por setor. Um setor tem no maximo um horario por
 * dia da semana: salvar de novo o mesmo setor/dia substitui o anterior.
 */
@Service
public class HorarioAtendimentoService {

	private final HorarioAtendimentoRepository repository;

	public HorarioAtendimentoService(HorarioAtendimentoRepository repository) {
		this.repository = repository;
	}

	public List<HorarioAtendimento> listar(String setor) {
		List<HorarioAtendimento> todos = setor == null || setor.isBlank() ? repository.findAll()
				: repository.findBySetor(setor.trim());
		return todos.stream()
				.sorted(Comparator.comparing(HorarioAtendimento::getSetor).thenComparing(HorarioAtendimento::getDiaSemana))
				.toList();
	}

	public HorarioAtendimento salvar(HorarioForm form) {
		if (!LocalTime.parse(form.getHorarioInicio()).isBefore(LocalTime.parse(form.getHorarioFim()))) {
			throw new RegraNegocioException("O horário de início deve ser anterior ao horário de fim.");
		}
		String setor = form.getSetor().trim();
		HorarioAtendimento horario = repository.findBySetorAndDiaSemana(setor, form.getDiaSemana())
				.orElseGet(HorarioAtendimento::new);
		horario.setSetor(setor);
		horario.setDiaSemana(form.getDiaSemana());
		horario.setHorarioInicio(form.getHorarioInicio());
		horario.setHorarioFim(form.getHorarioFim());
		return repository.save(horario);
	}

	public void excluir(String id) {
		repository.deleteById(id);
	}

	/**
	 * Indica se um chamado aberto naquele instante caiu dentro do expediente do
	 * setor. Setor sem horario cadastrado e considerado "sem restricao" (true).
	 */
	public static boolean dentroDoExpediente(Map<String, Map<DayOfWeek, HorarioAtendimento>> grade, String setor,
			Instant instante) {
		Map<DayOfWeek, HorarioAtendimento> doSetor = grade.get(setor);
		if (doSetor == null || doSetor.isEmpty()) {
			return true;
		}
		ZonedDateTime local = instante.atZone(RelogioConfig.FUSO);
		HorarioAtendimento horario = doSetor.get(local.getDayOfWeek());
		return horario != null && horario.cobre(local.toLocalTime());
	}

	/** Grade setor -> dia -> horario, para checar varios chamados sem ir ao banco a cada um. */
	public Map<String, Map<DayOfWeek, HorarioAtendimento>> grade() {
		return repository.findAll().stream().collect(Collectors.groupingBy(HorarioAtendimento::getSetor,
				Collectors.toMap(HorarioAtendimento::getDiaSemana, h -> h, (a, b) -> b)));
	}

}
