package br.umc.thindesk.chamado;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChamadoRepository extends MongoRepository<Chamado, String> {

	List<Chamado> findTop50ByTecnicoUsuarioIdOrderByDataAberturaDesc(String usuarioId);

	List<Chamado> findTop50BySolicitanteUsuarioIdOrderByDataAberturaDesc(String usuarioId);

}
