package br.umc.thindesk.horario;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface HorarioAtendimentoRepository extends MongoRepository<HorarioAtendimento, String> {

	List<HorarioAtendimento> findBySetor(String setor);

	Optional<HorarioAtendimento> findBySetorAndDiaSemana(String setor, DayOfWeek diaSemana);

}
