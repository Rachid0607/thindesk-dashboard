package br.umc.thindesk.usuario;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tecnico")
public class TecnicoController {

	private final UsuarioService usuarioService;

	public TecnicoController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@GetMapping
	public String raiz() {
		return "redirect:/tecnico/solicitantes";
	}

	@GetMapping("/solicitantes")
	public String listarSolicitantes(Model model) {
		model.addAttribute("solicitantes", usuarioService.listarPorPerfil(Perfil.SOLICITANTE));
		return "usuario/tecnico-solicitantes";
	}

}
