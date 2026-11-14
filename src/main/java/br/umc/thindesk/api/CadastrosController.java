package br.umc.thindesk.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.umc.thindesk.api.dto.ClienteDTO;
import br.umc.thindesk.api.dto.HorarioAtendimentoDTO;
import br.umc.thindesk.cliente.Cliente;
import br.umc.thindesk.cliente.ClienteForm;
import br.umc.thindesk.cliente.ClienteService;
import br.umc.thindesk.horario.HorarioAtendimentoService;
import jakarta.validation.Valid;

/**
 * Cadastros herdados do Thindesk original expostos na API v1: clientes
 * (ADMINISTRADOR e TECNICO) e horarios de atendimento (ADMINISTRADOR).
 */
@RestController
@RequestMapping("/api/v1")
public class CadastrosController {

	private final ClienteService clienteService;
	private final HorarioAtendimentoService horarioService;

	public CadastrosController(ClienteService clienteService, HorarioAtendimentoService horarioService) {
		this.clienteService = clienteService;
		this.horarioService = horarioService;
	}

	@GetMapping("/clientes")
	public List<ClienteDTO> listarClientes(@RequestParam(required = false) String setor) {
		return clienteService.listar(setor).stream().map(ClienteDTO::de).toList();
	}

	@PostMapping("/clientes")
	public ResponseEntity<ClienteDTO> cadastrarCliente(@Valid @RequestBody ClienteForm form) {
		Cliente cliente = clienteService.cadastrar(form);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(cliente.getId()).toUri();
		return ResponseEntity.created(local).body(ClienteDTO.de(cliente));
	}

	@GetMapping("/horarios-atendimento")
	public List<HorarioAtendimentoDTO> listarHorarios(@RequestParam(required = false) String setor) {
		return horarioService.listar(setor).stream().map(HorarioAtendimentoDTO::de).toList();
	}

}
