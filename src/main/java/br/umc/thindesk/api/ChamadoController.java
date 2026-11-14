package br.umc.thindesk.api;

import java.net.URI;
import java.time.Clock;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.umc.thindesk.api.dto.AlterarStatusRequest;
import br.umc.thindesk.api.dto.ChamadoDetalheDTO;
import br.umc.thindesk.api.dto.ChamadoResumoDTO;
import br.umc.thindesk.api.dto.NovaConsideracaoRequest;
import br.umc.thindesk.api.dto.NovoChamadoRequest;
import br.umc.thindesk.api.dto.PaginaDTO;
import br.umc.thindesk.chamado.Chamado;
import br.umc.thindesk.chamado.ChamadoConsultaService;
import br.umc.thindesk.chamado.ChamadoService;
import br.umc.thindesk.chamado.Pessoa;
import br.umc.thindesk.cliente.Cliente;
import br.umc.thindesk.cliente.ClienteService;
import br.umc.thindesk.comum.RecursoNaoEncontradoException;
import br.umc.thindesk.comum.RegraNegocioException;
import br.umc.thindesk.dashboard.FiltroChamados;
import br.umc.thindesk.seguranca.UsuarioAtual;
import jakarta.validation.Valid;

/**
 * Consulta e manutencao de chamados. O SOLICITANTE so ve os proprios
 * chamados; ADMINISTRADOR e TECNICO veem todos.
 */
@RestController
@RequestMapping("/api/v1/chamados")
public class ChamadoController {

	private final ChamadoService chamadoService;
	private final ChamadoConsultaService consultaService;
	private final ClienteService clienteService;
	private final Clock clock;

	public ChamadoController(ChamadoService chamadoService, ChamadoConsultaService consultaService,
			ClienteService clienteService, Clock clock) {
		this.chamadoService = chamadoService;
		this.consultaService = consultaService;
		this.clienteService = clienteService;
		this.clock = clock;
	}

	@GetMapping
	public PaginaDTO<ChamadoResumoDTO> listar(@ModelAttribute FiltroRequest filtro,
			@RequestParam(required = false) String busca,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String sort,
			Authentication authentication) {
		UsuarioAtual usuario = UsuarioAtual.de(authentication);
		String restricao = usuario.isSolicitante() ? usuario.pessoa().usuarioId() : null;
		return consultaService.listar(FiltroChamados.paraListagem(filtro), busca, page, size, sort, restricao);
	}

	@GetMapping("/{id}")
	public ChamadoDetalheDTO detalhar(@PathVariable String id, Authentication authentication) {
		Chamado chamado = chamadoService.buscar(id);
		verificarAcesso(chamado, UsuarioAtual.de(authentication));
		return ChamadoDetalheDTO.de(chamado, clock.instant());
	}

	@PostMapping
	public ResponseEntity<ChamadoDetalheDTO> abrir(@Valid @RequestBody NovoChamadoRequest req,
			Authentication authentication) {
		UsuarioAtual usuario = UsuarioAtual.de(authentication);
		Pessoa solicitante = usuario.pessoa();
		String setor = req.setor();
		if (req.clienteId() != null && !req.clienteId().isBlank()) {
			if (usuario.isSolicitante()) {
				throw new AccessDeniedException("Solicitantes abrem chamados apenas em nome próprio.");
			}
			Cliente cliente;
			try {
				cliente = clienteService.buscar(req.clienteId());
			} catch (RecursoNaoEncontradoException e) {
				throw new RegraNegocioException("Cliente " + req.clienteId() + " não encontrado.");
			}
			solicitante = new Pessoa(cliente.getId(), cliente.getNome());
			setor = cliente.getSetor();
		}
		if (setor == null || setor.isBlank()) {
			throw new RegraNegocioException("Informe o setor ou o cliente (clienteId) do chamado.");
		}
		Chamado chamado = chamadoService.abrir(req.titulo(), req.descricao(), req.tipo(), req.prioridade(), setor,
				solicitante);
		URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(chamado.getId()).toUri();
		return ResponseEntity.created(local).body(ChamadoDetalheDTO.de(chamado, clock.instant()));
	}

	@PatchMapping("/{id}/status")
	public ChamadoDetalheDTO alterarStatus(@PathVariable String id, @Valid @RequestBody AlterarStatusRequest req,
			Authentication authentication) {
		UsuarioAtual usuario = UsuarioAtual.de(authentication);
		Chamado chamado = chamadoService.alterarStatus(id, req.status(), req.tecnicoId(), req.comentario(),
				usuario.pessoa(), usuario.perfil());
		return ChamadoDetalheDTO.de(chamado, clock.instant());
	}

	@PostMapping("/{id}/consideracoes")
	public ResponseEntity<ChamadoDetalheDTO> adicionarConsideracao(@PathVariable String id,
			@Valid @RequestBody NovaConsideracaoRequest req, Authentication authentication) {
		UsuarioAtual usuario = UsuarioAtual.de(authentication);
		Chamado chamado = chamadoService.adicionarConsideracao(id, req.texto(), usuario.pessoa());
		return ResponseEntity.status(201).body(ChamadoDetalheDTO.de(chamado, clock.instant()));
	}

	private static void verificarAcesso(Chamado chamado, UsuarioAtual usuario) {
		if (usuario.isSolicitante() && (chamado.getSolicitante() == null
				|| !usuario.pessoa().usuarioId().equals(chamado.getSolicitante().usuarioId()))) {
			throw new AccessDeniedException("Chamado de outro solicitante.");
		}
	}

}
