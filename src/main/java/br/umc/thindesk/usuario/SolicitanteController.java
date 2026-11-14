package br.umc.thindesk.usuario;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.umc.thindesk.chamado.ChamadoRepository;
import br.umc.thindesk.seguranca.UsuarioAutenticado;

/**
 * Area do SOLICITANTE: dados da conta e os chamados que ele abriu.
 */
@Controller
@RequestMapping("/solicitante")
public class SolicitanteController {

	private final ChamadoRepository chamadoRepository;

	public SolicitanteController(ChamadoRepository chamadoRepository) {
		this.chamadoRepository = chamadoRepository;
	}

	@GetMapping
	public String meusDados(Model model, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		model.addAttribute("usuario", usuario);
		return "usuario/solicitante-meus-dados";
	}

	@GetMapping("/chamados")
	public String meusChamados(Model model, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		model.addAttribute("chamados",
				chamadoRepository.findTop50BySolicitanteUsuarioIdOrderByDataAberturaDesc(usuario.getId()));
		model.addAttribute("titulo", "Meus chamados");
		model.addAttribute("subtitulo", "Últimos 50 chamados abertos por você.");
		return "chamado/lista-chamados";
	}

}
