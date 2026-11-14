package br.umc.thindesk.cliente;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClienteRepository extends MongoRepository<Cliente, String> {

	List<Cliente> findAllByOrderByNomeAsc();

	List<Cliente> findBySetorOrderByNomeAsc(String setor);

	boolean existsByNomeIgnoreCase(String nome);

}
