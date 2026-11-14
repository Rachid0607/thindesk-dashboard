package br.umc.thindesk.usuario;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.umc.thindesk.chamado.ChamadoRepository;
import br.umc.thindesk.seguranca.UsuarioAutenticado;

/**
 * Area do TECNICO: lista os ultimos chamados atribuidos a ele.
 */
@Controller
@RequestMapping("/tecnico")
public class TecnicoController {

	private final ChamadoRepository chamadoRepository;

	public TecnicoController(ChamadoRepository chamadoRepository) {
		this.chamadoRepository = chamadoRepository;
	}

	@GetMapping
	public String raiz() {
		return "redirect:/tecnico/chamados";
	}

	@GetMapping("/chamados")
	public String meusChamados(Model model, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		model.addAttribute("chamados", chamadoRepository.findTop50ByTecnicoUsuarioIdOrderByDataAberturaDesc(usuario.getId()));
		model.addAttribute("titulo", "Meus atendimentos");
		model.addAttribute("subtitulo", "Últimos 50 chamados atribuídos a você.");
		return "chamado/lista-chamados";
	}

}
