package br.umc.thindesk.auditoria;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditoriaRepository extends MongoRepository<EventoAuditoria, String> {

	List<EventoAuditoria> findTop100ByOrderByDataHoraDesc();

}
