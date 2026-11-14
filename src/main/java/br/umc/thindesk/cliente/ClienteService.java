package br.umc.thindesk.cliente;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import br.umc.thindesk.comum.RecursoNaoEncontradoException;

@Service
public class ClienteService {

	private final ClienteRepository clienteRepository;
	private final Clock clock;

	public ClienteService(ClienteRepository clienteRepository, Clock clock) {
		this.clienteRepository = clienteRepository;
		this.clock = clock;
	}

	public List<Cliente> listar(String setor) {
		return setor == null || setor.isBlank() ? clienteRepository.findAllByOrderByNomeAsc()
				: clienteRepository.findBySetorOrderByNomeAsc(setor.trim());
	}

	public Cliente buscar(String id) {
		return clienteRepository.findById(id)
				.orElseThrow(() -> new RecursoNaoEncontradoException("Cliente " + id + " não encontrado."));
	}

	public Cliente cadastrar(ClienteForm form) {
		Cliente cliente = new Cliente();
		cliente.setNome(form.getNome().trim());
		cliente.setTelefone(vazioParaNulo(form.getTelefone()));
		cliente.setSetor(form.getSetor().trim());
		cliente.setEmail(vazioParaNulo(form.getEmail()));
		cliente.setCriadoEm(clock.instant());
		return clienteRepository.save(cliente);
	}

	private static String vazioParaNulo(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}

}
